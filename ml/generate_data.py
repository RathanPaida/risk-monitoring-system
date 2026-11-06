"""
Synthetic data generator for Transaction Risk Monitoring System.
Generates customers, accounts, merchants, devices, transactions, and synthetic fraud labels.
"""

import os
import random
import hashlib
from datetime import datetime, timedelta, timezone
from pathlib import Path
import numpy as np
import pandas as pd
from faker import Faker

def generate_dataset(
    output_dir: str = "data",
    seed: int = 42,
    num_customers: int = 2000,
    num_merchants: int = 500,
    num_transactions: int = 105000,
    start_date: datetime = datetime(2026, 8, 1, 0, 0, 0, tzinfo=timezone.utc),
    days: int = 90
):
    """
    Generate synthetic banking datasets with injected fraud patterns.
    """
    random.seed(seed)
    np.random.seed(seed)
    fake = Faker()
    Faker.seed(seed)

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    # 1. Customers
    countries = ["US", "GB", "CA", "DE", "FR"]
    country_weights = [0.75, 0.10, 0.08, 0.04, 0.03]

    customers = []
    base_customer_created = start_date - timedelta(days=365)
    for c_id in range(1, num_customers + 1):
        home_country = random.choices(countries, weights=country_weights)[0]
        days_offset = random.randint(0, 300)
        created_at = base_customer_created + timedelta(days=days_offset, seconds=random.randint(0, 86400))
        customers.append({
            "id": c_id,
            "name": fake.name(),
            "home_country": home_country,
            "created_at": created_at.isoformat()
        })
    df_customers = pd.DataFrame(customers)

    # 2. Accounts (1-2 per customer)
    accounts = []
    account_id = 1
    for cust in customers:
        num_accs = random.choices([1, 2], weights=[0.6, 0.4])[0]
        acc_types = ["CHECKING", "SAVINGS"] if num_accs == 2 else [random.choice(["CHECKING", "SAVINGS"])]
        for a_type in acc_types:
            cust_created = datetime.fromisoformat(cust["created_at"])
            opened_at = cust_created + timedelta(days=random.randint(0, 30), seconds=random.randint(0, 86400))
            accounts.append({
                "id": account_id,
                "customer_id": cust["id"],
                "account_type": a_type,
                "opened_at": opened_at.isoformat(),
                "status": "ACTIVE"
            })
            account_id += 1
    df_accounts = pd.DataFrame(accounts)

    # 3. Merchants (500)
    categories = [
        "GROCERY", "ELECTRONICS", "TRAVEL", "RESTAURANT", "CLOTHING",
        "ENTERTAINMENT", "UTILITIES", "GAS_STATION", "HEALTHCARE", "SERVICES"
    ]
    merchants = []
    for m_id in range(1, num_merchants + 1):
        cat = random.choice(categories)
        country = random.choices(["US", "GB", "CA", "DE", "FR", "JP", "MX"], weights=[0.80, 0.05, 0.04, 0.03, 0.03, 0.03, 0.02])[0]
        merchants.append({
            "id": m_id,
            "name": f"{fake.company()} {cat.capitalize()}",
            "category": cat,
            "country": country
        })
    df_merchants = pd.DataFrame(merchants)

    # 4. Devices (1-3 per customer)
    devices = []
    device_id = 1
    customer_device_map = {}
    for cust in customers:
        num_devs = random.choices([1, 2, 3], weights=[0.65, 0.25, 0.10])[0]
        customer_device_map[cust["id"]] = []
        cust_created = datetime.fromisoformat(cust["created_at"])
        for d_idx in range(num_devs):
            dev_hash = hashlib.sha256(f"dev_{cust['id']}_{d_idx}_{seed}".encode()).hexdigest()[:16]
            first_seen = cust_created + timedelta(days=random.randint(0, 45), seconds=random.randint(0, 86400))
            dev_record = {
                "id": device_id,
                "customer_id": cust["id"],
                "device_hash": dev_hash,
                "first_seen_at": first_seen.isoformat()
            }
            devices.append(dev_record)
            customer_device_map[cust["id"]].append(device_id)
            device_id += 1
    df_devices = pd.DataFrame(devices)

    # Quick lookup maps
    account_to_customer = {a["id"]: a["customer_id"] for a in accounts}
    customer_to_country = {c["id"]: c["home_country"] for c in customers}
    account_ids = [a["id"] for a in accounts]
    # Pareto-distributed account activity weights
    raw_weights = np.random.pareto(a=1.5, size=len(account_ids)) + 1.0
    account_weights = raw_weights / raw_weights.sum()

    # 5. Transactions & Injected Fraud
    transactions = []
    labels = []

    # Target: ~1.5% fraud overall (1,575 fraud txns out of 105,000)
    target_fraud_count = int(num_transactions * 0.015)
    
    # We will generate base transactions, then inject fraud bursts and specific patterns
    channels = ["ONLINE", "POS", "ATM"]
    channel_weights = [0.45, 0.45, 0.10]

    foreign_destinations = ["RU", "NG", "BR", "CN", "ZA", "TR", "RO"]

    # Pre-generate timestamps with diurnal pattern (higher activity during 08:00 - 22:00)
    total_seconds = days * 86400
    random_second_offsets = np.random.uniform(0, total_seconds, size=num_transactions)
    # Add diurnal adjustment: modulate probability of hour
    timestamps = [start_date + timedelta(seconds=float(s)) for s in random_second_offsets]
    timestamps.sort()

    txn_id = 1
    fraud_count = 0

    # Index for inserting burst frauds
    burst_count = max(0, min(int(num_transactions * 0.001), 90))
    if num_transactions > 100 and burst_count > 0:
        start_idx = max(10, int(num_transactions * 0.05))
        end_idx = max(start_idx + burst_count + 1, int(num_transactions * 0.95))
        available_range = list(range(start_idx, end_idx))
        actual_burst_count = min(burst_count, len(available_range))
        burst_points = set(np.random.choice(available_range, size=actual_burst_count, replace=False))
    else:
        burst_points = set()

    i = 0
    while i < num_transactions:
        t_time = timestamps[i]
        
        # Check if this point triggers a velocity burst fraud
        if i in burst_points and fraud_count < target_fraud_count - 10:
            acc_id = np.random.choice(account_ids, p=account_weights)
            cust_id = account_to_customer[acc_id]
            dev_id = customer_device_map[cust_id][0]
            burst_len = random.randint(7, 12)
            burst_window_seconds = random.randint(120, 480) # 2-8 minutes
            burst_delta = burst_window_seconds / burst_len

            for b in range(burst_len):
                if i >= num_transactions:
                    break
                b_time = t_time + timedelta(seconds=b * burst_delta)
                amt = round(float(np.random.uniform(50.0, 350.0)), 2)
                merch_id = random.randint(1, num_merchants)
                
                transactions.append({
                    "id": txn_id,
                    "account_id": acc_id,
                    "merchant_id": merch_id,
                    "device_id": dev_id,
                    "amount": amt,
                    "currency": "USD",
                    "country": customer_to_country[cust_id],
                    "txn_time": b_time.isoformat(),
                    "channel": "ONLINE",
                    "created_at": (b_time + timedelta(milliseconds=random.randint(100, 900))).isoformat()
                })
                labels.append({
                    "txn_id": txn_id,
                    "is_fraud": True,
                    "fraud_type": "velocity_burst"
                })
                txn_id += 1
                fraud_count += 1
                i += 1
            continue

        # Standard selection
        acc_id = np.random.choice(account_ids, p=account_weights)
        cust_id = account_to_customer[acc_id]
        home_country = customer_to_country[cust_id]
        cust_devs = customer_device_map[cust_id]
        dev_id = cust_devs[0] if random.random() < 0.85 else (cust_devs[1] if len(cust_devs) > 1 else cust_devs[0])
        channel = random.choices(channels, weights=channel_weights)[0]
        merch_id = random.randint(1, num_merchants)
        country = home_country

        # Determine if this transaction is an injected fraud
        is_fraud = False
        fraud_type = "none"

        # If we need more fraud, select with small probability
        if fraud_count < target_fraud_count and random.random() < 0.018:
            is_fraud = True
            f_choice = random.choices(
                ["amount_spike", "foreign_country", "new_device_large", "mixed", "subtle"],
                weights=[0.30, 0.25, 0.20, 0.15, 0.10]
            )[0]

            if f_choice == "amount_spike":
                fraud_type = "amount_spike"
                # Massive spike: $2,500 - $9,500
                amt = round(float(np.random.uniform(2500.0, 9500.0)), 2)
            elif f_choice == "foreign_country":
                fraud_type = "foreign_country"
                country = random.choice(foreign_destinations)
                amt = round(float(np.exp(np.random.normal(4.0, 0.8))), 2)
                amt = min(amt, 3000.0)
            elif f_choice == "new_device_large":
                fraud_type = "new_device_large"
                # Brand new unregistered device id
                dev_id = device_id
                # Add to devices table for referential integrity
                devices.append({
                    "id": dev_id,
                    "customer_id": cust_id,
                    "device_hash": hashlib.sha256(f"dev_fraud_{txn_id}".encode()).hexdigest()[:16],
                    "first_seen_at": t_time.isoformat()
                })
                device_id += 1
                amt = round(float(np.random.uniform(850.0, 4000.0)), 2)
            elif f_choice == "mixed":
                fraud_type = "mixed"
                country = random.choice(foreign_destinations)
                dev_id = device_id
                devices.append({
                    "id": dev_id,
                    "customer_id": cust_id,
                    "device_hash": hashlib.sha256(f"dev_mixed_{txn_id}".encode()).hexdigest()[:16],
                    "first_seen_at": t_time.isoformat()
                })
                device_id += 1
                amt = round(float(np.random.uniform(1200.0, 5000.0)), 2)
            elif f_choice == "subtle":
                # Subtle fraud: rules might miss (e.g. slightly elevated amount or unusual hour/channel)
                fraud_type = "subtle"
                amt = round(float(np.random.uniform(280.0, 480.0)), 2)
                channel = "ONLINE"

            fraud_count += 1
        else:
            # Normal transaction
            # Lognormal amount (typical $15 - $150, rare large purchase)
            amt = round(float(np.exp(np.random.normal(3.5, 0.85))), 2)
            amt = max(2.50, min(amt, 4500.0))

            # Normal occasional oddities (to test rule false-positives):
            # 1. Normal vacation travel (1.5% of normal)
            if random.random() < 0.015:
                country = random.choice(["GB", "FR", "IT", "ES", "JP", "MX"])
            # 2. Normal legitimate big purchase ($1,200 - $2,500, e.g. appliance, electronics)
            elif random.random() < 0.005:
                amt = round(float(np.random.uniform(1200.0, 2600.0)), 2)

        transactions.append({
            "id": txn_id,
            "account_id": acc_id,
            "merchant_id": merch_id,
            "device_id": dev_id,
            "amount": amt,
            "currency": "USD",
            "country": country,
            "txn_time": t_time.isoformat(),
            "channel": channel,
            "created_at": (t_time + timedelta(milliseconds=random.randint(50, 950))).isoformat()
        })
        labels.append({
            "txn_id": txn_id,
            "is_fraud": is_fraud,
            "fraud_type": fraud_type if is_fraud else ""
        })
        txn_id += 1
        i += 1

    # Re-wrap updated devices into DataFrame
    df_devices = pd.DataFrame(devices)
    df_transactions = pd.DataFrame(transactions)
    df_labels = pd.DataFrame(labels)

    # Sort transactions chronologically
    df_transactions["txn_time_dt"] = pd.to_datetime(df_transactions["txn_time"])
    df_transactions = df_transactions.sort_values("txn_time_dt").reset_index(drop=True)
    df_transactions["id"] = range(1, len(df_transactions) + 1)
    df_labels["txn_id"] = df_transactions["id"]
    df_transactions = df_transactions.drop(columns=["txn_time_dt"])

    # Save to CSV
    df_customers.to_csv(output_path / "customers.csv", index=False)
    df_accounts.to_csv(output_path / "accounts.csv", index=False)
    df_merchants.to_csv(output_path / "merchants.csv", index=False)
    df_devices.to_csv(output_path / "devices.csv", index=False)
    df_transactions.to_csv(output_path / "transactions.csv", index=False)
    df_labels.to_csv(output_path / "labels.csv", index=False)

    print(f"Generated dataset in '{output_dir}':")
    print(f"  Customers:    {len(df_customers):,}")
    print(f"  Accounts:     {len(df_accounts):,}")
    print(f"  Merchants:    {len(df_merchants):,}")
    print(f"  Devices:      {len(df_devices):,}")
    print(f"  Transactions: {len(df_transactions):,}")
    print(f"  Fraud Count:  {df_labels['is_fraud'].sum():,} ({df_labels['is_fraud'].mean() * 100:.2f}%)")

    return {
        "customers": df_customers,
        "accounts": df_accounts,
        "merchants": df_merchants,
        "devices": df_devices,
        "transactions": df_transactions,
        "labels": df_labels
    }

if __name__ == "__main__":
    generate_dataset()
