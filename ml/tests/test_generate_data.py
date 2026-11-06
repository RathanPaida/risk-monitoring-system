import pytest
import pandas as pd
from pathlib import Path
from ml.generate_data import generate_dataset

def test_generate_data_small(tmp_path):
    """Test generating a scaled-down dataset for fast verification."""
    data = generate_dataset(
        output_dir=str(tmp_path),
        seed=123,
        num_customers=50,
        num_merchants=20,
        num_transactions=1000,
        days=10
    )

    # Verify return dictionary keys
    assert "customers" in data
    assert "accounts" in data
    assert "merchants" in data
    assert "devices" in data
    assert "transactions" in data
    assert "labels" in data

    # Verify row counts
    assert len(data["customers"]) == 50
    assert len(data["merchants"]) == 20
    assert len(data["transactions"]) == 1000
    assert len(data["labels"]) == 1000
    assert len(data["accounts"]) >= 50
    assert len(data["devices"]) >= 50

    # Verify no nulls in required columns
    assert not data["customers"].isnull().any().any()
    assert not data["accounts"].isnull().any().any()
    assert not data["merchants"].isnull().any().any()
    assert not data["devices"].isnull().any().any()
    assert not data["transactions"].isnull().any().any()
    assert not data["labels"]["is_fraud"].isnull().any()

    # Verify positive amounts
    assert (data["transactions"]["amount"] > 0).all()

    # Verify fraud rate within realistic bounds (1% to 3% on small sample)
    fraud_rate = data["labels"]["is_fraud"].mean()
    assert 0.005 <= fraud_rate <= 0.05

def test_seed_reproducibility(tmp_path):
    """Test that two runs with the same seed yield identical outputs."""
    dir1 = tmp_path / "run1"
    dir2 = tmp_path / "run2"

    d1 = generate_dataset(str(dir1), seed=99, num_customers=30, num_merchants=10, num_transactions=200, days=5)
    d2 = generate_dataset(str(dir2), seed=99, num_customers=30, num_merchants=10, num_transactions=200, days=5)

    pd.testing.assert_frame_equal(d1["transactions"], d2["transactions"])
    pd.testing.assert_frame_equal(d1["labels"], d2["labels"])
    pd.testing.assert_frame_equal(d1["customers"], d2["customers"])

def test_files_written_to_disk(tmp_path):
    """Test that all CSV files are successfully written to disk."""
    generate_dataset(str(tmp_path), seed=42, num_customers=20, num_merchants=10, num_transactions=100, days=2)
    expected_files = [
        "customers.csv", "accounts.csv", "merchants.csv",
        "devices.csv", "transactions.csv", "labels.csv"
    ]
    for fname in expected_files:
        assert (tmp_path / fname).exists(), f"File {fname} was not created"
        assert (tmp_path / fname).stat().st_size > 0
