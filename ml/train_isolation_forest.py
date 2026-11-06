"""
Trains an Isolation Forest anomaly detection model on historical transactions.
Enforces a strict time-based train/test split to prevent temporal leakage.
"""

import os
import sys
from pathlib import Path
from datetime import datetime

# Ensure root repository is in sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from ml.features import extract_features, FEATURE_COLUMNS

def train_model(
    data_dir: str = "data",
    model_output_path: str = "ml/isolation_forest.joblib",
    train_ratio: float = 0.77, # ~70 days out of 90 days
    random_state: int = 42,
    contamination: float = 0.015
):
    data_path = Path(data_dir)
    print(f"Loading data from '{data_dir}'...")

    df_txns = pd.read_csv(data_path / "transactions.csv")
    df_customers = pd.read_csv(data_path / "customers.csv")
    df_accounts = pd.read_csv(data_path / "accounts.csv")

    df_txns["txn_time"] = pd.to_datetime(df_txns["txn_time"])
    df_txns = df_txns.sort_values("txn_time").reset_index(drop=True)

    print(f"Extracting features across {len(df_txns):,} transactions...")
    df_features, feature_cols = extract_features(df_txns, df_customers, df_accounts)

    # Time-based train/test split
    split_idx = int(len(df_txns) * train_ratio)
    split_time = df_txns.iloc[split_idx]["txn_time"]

    train_mask = df_txns["txn_time"] < split_time
    test_mask = df_txns["txn_time"] >= split_time

    df_train = df_features[train_mask]
    df_test = df_features[test_mask]

    X_train = df_train[feature_cols].to_numpy()
    X_test = df_test[feature_cols].to_numpy()

    print(f"Time-based split at {split_time}:")
    print(f"  Train set: {len(df_train):,} transactions ({df_train.index[0]} to {df_train.index[-1]})")
    print(f"  Test set:  {len(df_test):,} transactions ({df_test.index[0]} to {df_test.index[-1]})")

    print(f"Training IsolationForest (n_estimators=100, contamination={contamination})...")
    model = IsolationForest(
        n_estimators=100,
        contamination=contamination,
        random_state=random_state,
        n_jobs=-1
    )
    model.fit(X_train)

    # Model evaluation on training set
    train_preds = model.predict(X_train)
    train_anomalies = (train_preds == -1).sum()
    print(f"Train anomalies flagged: {train_anomalies:,} ({train_anomalies / len(X_train) * 100:.2f}%)")

    # Save model
    out_path = Path(model_output_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, out_path)
    print(f"Saved trained Isolation Forest model to '{model_output_path}'.")

    return {
        "model": model,
        "split_time": split_time,
        "test_txn_ids": df_txns.loc[test_mask, "id"].values,
        "X_test": X_test,
        "feature_cols": feature_cols
    }

if __name__ == "__main__":
    train_model()
