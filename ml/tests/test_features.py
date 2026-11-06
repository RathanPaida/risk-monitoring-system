import pytest
import pandas as pd
import numpy as np
from datetime import datetime, timezone, timedelta
from ml.features import extract_features, FEATURE_COLUMNS

def test_extract_features_basic():
    base_time = datetime(2026, 8, 1, 10, 0, 0, tzinfo=timezone.utc)

    customers = pd.DataFrame([
        {"id": 1, "name": "Alice", "home_country": "US", "created_at": "2026-01-01T00:00:00Z"},
        {"id": 2, "name": "Bob", "home_country": "GB", "created_at": "2026-01-01T00:00:00Z"}
    ])

    accounts = pd.DataFrame([
        {"id": 10, "customer_id": 1, "account_type": "CHECKING", "opened_at": "2026-01-02T00:00:00Z", "status": "ACTIVE"},
        {"id": 20, "customer_id": 2, "account_type": "CHECKING", "opened_at": "2026-01-02T00:00:00Z", "status": "ACTIVE"}
    ])

    txns = pd.DataFrame([
        # Txn 1: Alice, home country US, dev 100, $50
        {"id": 1, "account_id": 10, "merchant_id": 1, "device_id": 100, "amount": 50.0, "currency": "USD", "country": "US", "txn_time": base_time.isoformat(), "channel": "ONLINE"},
        # Txn 2: Alice, 2 mins later, dev 100, $150
        {"id": 2, "account_id": 10, "merchant_id": 2, "device_id": 100, "amount": 150.0, "currency": "USD", "country": "US", "txn_time": (base_time + timedelta(minutes=2)).isoformat(), "channel": "ONLINE"},
        # Txn 3: Alice, 15 mins later, dev 200 (new dev), foreign country GB
        {"id": 3, "account_id": 10, "merchant_id": 3, "device_id": 200, "amount": 600.0, "currency": "USD", "country": "GB", "txn_time": (base_time + timedelta(minutes=17)).isoformat(), "channel": "POS"},
        # Txn 4: Bob, home country GB
        {"id": 4, "account_id": 20, "merchant_id": 4, "device_id": 300, "amount": 80.0, "currency": "USD", "country": "GB", "txn_time": (base_time + timedelta(minutes=5)).isoformat(), "channel": "ATM"}
    ])

    df_feats, cols = extract_features(txns, customers, accounts)

    assert cols == FEATURE_COLUMNS
    assert len(df_feats) == 4
    assert not df_feats.isnull().any().any()

    # Txn 1 checks
    row1 = df_feats[df_feats["id"] == 1].iloc[0]
    assert row1["country_differs_from_home"] == 0
    assert row1["is_new_device"] == 1
    assert row1["txns_last_10m"] == 0
    assert row1["channel_ONLINE"] == 1
    assert row1["channel_POS"] == 0

    # Txn 2 checks (2 min after txn 1)
    row2 = df_feats[df_feats["id"] == 2].iloc[0]
    assert row2["txns_last_10m"] == 1 # 1 prior txn in 10m
    assert row2["is_new_device"] == 0 # dev 100 already seen
    assert pytest.approx(row2["hours_since_prev_txn"], 0.01) == 2.0 / 60.0

    # Txn 3 checks (new device, country GB != US)
    row3 = df_feats[df_feats["id"] == 3].iloc[0]
    assert row3["country_differs_from_home"] == 1
    assert row3["is_new_device"] == 1 # dev 200 is new
    assert row3["channel_POS"] == 1
