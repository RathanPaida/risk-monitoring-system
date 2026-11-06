"""
Feature engineering pipeline for Transaction Risk Monitoring System.
Extracts behavioral, temporal, geographic, and device features without target leakage.
"""

from typing import List, Tuple
import numpy as np
import pandas as pd

FEATURE_COLUMNS = [
    "amount",
    "amount_to_30d_mean",
    "txns_last_10m",
    "hours_since_prev_txn",
    "country_differs_from_home",
    "is_new_device",
    "hour_of_day",
    "channel_ONLINE",
    "channel_POS",
    "channel_ATM"
]

def extract_features(
    df_txns: pd.DataFrame,
    df_customers: pd.DataFrame,
    df_accounts: pd.DataFrame
) -> Tuple[pd.DataFrame, List[str]]:
    """
    Extracts features for each transaction in df_txns using customer and account context.
    Strictly avoids target leakage (is_fraud is never accessed).
    """
    df = df_txns.copy()
    if not pd.api.types.is_datetime64_any_dtype(df["txn_time"]):
        df["txn_time"] = pd.to_datetime(df["txn_time"])

    # Ensure chronological order
    df = df.sort_values("txn_time").reset_index(drop=True)

    # 1. Join account and customer context
    account_cust_map = df_accounts.set_index("id")["customer_id"].to_dict()
    customer_country_map = df_customers.set_index("id")["home_country"].to_dict()

    df["customer_id"] = df["account_id"].map(account_cust_map)
    df["home_country"] = df["customer_id"].map(customer_country_map)

    # Feature: country_differs_from_home
    df["country_differs_from_home"] = (df["country"] != df["home_country"]).astype(int)

    # Feature: hour_of_day
    df["hour_of_day"] = df["txn_time"].dt.hour

    # Feature: One-hot channels
    for ch in ["ONLINE", "POS", "ATM"]:
        df[f"channel_{ch}"] = (df["channel"] == ch).astype(int)

    # 2. Sequential historical features computed per account
    amounts = df["amount"].to_numpy(dtype=float)
    account_ids = df["account_id"].to_numpy()
    device_ids = df["device_id"].to_numpy()
    timestamps = pd.to_datetime(df["txn_time"], utc=True).to_numpy(dtype="datetime64[ns]").astype("int64")

    n = len(df)
    amount_to_30d_mean = np.ones(n, dtype=float)
    txns_last_10m = np.zeros(n, dtype=int)
    hours_since_prev_txn = np.full(n, 720.0, dtype=float) # default 30 days if first
    is_new_device = np.zeros(n, dtype=int)

    # Fast in-memory state tracking
    account_history = {} # acc_id -> deque of (timestamp_ns, amount)
    account_last_time = {} # acc_id -> timestamp_ns
    account_devices = {} # acc_id -> set of device_ids

    ten_min_ns = 10 * 60 * 1_000_000_000
    thirty_days_ns = 30 * 86400 * 1_000_000_000

    for i in range(n):
        acc = account_ids[i]
        dev = device_ids[i]
        t = int(timestamps[i])
        amt = amounts[i]

        # Device feature
        dev_set = account_devices.setdefault(acc, set())
        if dev not in dev_set:
            is_new_device[i] = 1
            dev_set.add(dev)
        else:
            is_new_device[i] = 0

        # Hours since previous transaction
        if acc in account_last_time:
            diff_ns = t - account_last_time[acc]
            hours_since_prev_txn[i] = max(0.0, diff_ns / (3600.0 * 1_000_000_000.0))
        account_last_time[acc] = t

        # Sliding window history for 10-min velocity and 30-day mean
        hist = account_history.setdefault(acc, [])
        # Prune older than 30 days
        cutoff_30d = t - thirty_days_ns
        cutoff_10m = t - ten_min_ns

        # Filter in-place
        new_hist = [item for item in hist if item[0] >= cutoff_30d]
        account_history[acc] = new_hist

        # Count in last 10m (strictly before current or including)
        cnt_10m = sum(1 for item in new_hist if item[0] >= cutoff_10m)
        txns_last_10m[i] = cnt_10m

        # Compute 30d trailing mean
        if new_hist:
            mean_30d = sum(item[1] for item in new_hist) / len(new_hist)
            amount_to_30d_mean[i] = amt / mean_30d if mean_30d > 0 else 1.0
        else:
            amount_to_30d_mean[i] = 1.0

        new_hist.append((t, amt))

    df["amount_to_30d_mean"] = amount_to_30d_mean
    df["txns_last_10m"] = txns_last_10m
    df["hours_since_prev_txn"] = hours_since_prev_txn
    df["is_new_device"] = is_new_device

    return df[["id"] + FEATURE_COLUMNS], FEATURE_COLUMNS

