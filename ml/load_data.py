"""
Bulk data loader for Transaction Risk Monitoring System.
Loads synthetic CSV datasets into PostgreSQL using batch operations.
"""

import os
import sys
import argparse
from pathlib import Path
import psycopg2
from psycopg2.extras import execute_values
import pandas as pd

def get_connection(host=None, port=None, dbname=None, user=None, password=None):
    host = host or os.getenv("DB_HOST", "localhost")
    port = port or os.getenv("DB_PORT", "5433")
    dbname = dbname or os.getenv("DB_NAME", "riskmonitor")
    user = user or os.getenv("DB_USER", "postgres")
    password = password or os.getenv("DB_PASSWORD", "postgres")

    return psycopg2.connect(
        host=host,
        port=port,
        dbname=dbname,
        user=user,
        password=password
    )

def load_table(conn, table_name, csv_path, columns, batch_size=2000):
    if not csv_path.exists():
        print(f"File {csv_path} not found, skipping {table_name}.")
        return 0

    df = pd.read_csv(csv_path)
    # Replace NaN with None for SQL NULL
    df = df.where(pd.notnull(df), None)

    # Ensure required columns
    df_cols = [c for c in columns if c in df.columns]
    records = [tuple(x) for x in df[df_cols].to_numpy()]

    cols_str = ", ".join(df_cols)
    query = f"INSERT INTO {table_name} ({cols_str}) VALUES %s ON CONFLICT DO NOTHING"

    with conn.cursor() as cur:
        execute_values(cur, query, records, page_size=batch_size)
    conn.commit()

    print(f"Loaded {len(records):,} records into '{table_name}'.")
    return len(records)

def load_all_data(data_dir="data", host=None, port=None, dbname=None, user=None, password=None):
    data_path = Path(data_dir)
    conn = get_connection(host, port, dbname, user, password)

    try:
        print("Starting bulk data load...")

        # 1. Customers
        load_table(
            conn, "customers",
            data_path / "customers.csv",
            ["id", "name", "home_country", "created_at"]
        )

        # 2. Accounts
        load_table(
            conn, "accounts",
            data_path / "accounts.csv",
            ["id", "customer_id", "account_type", "opened_at", "status"]
        )

        # 3. Merchants
        load_table(
            conn, "merchants",
            data_path / "merchants.csv",
            ["id", "name", "category", "country"]
        )

        # 4. Devices
        load_table(
            conn, "devices",
            data_path / "devices.csv",
            ["id", "customer_id", "device_hash", "first_seen_at"]
        )

        # 5. Transactions
        load_table(
            conn, "transactions",
            data_path / "transactions.csv",
            ["id", "account_id", "merchant_id", "device_id", "amount", "currency", "country", "txn_time", "channel", "created_at"]
        )

        # 6. Synthetic Labels (ML only)
        load_table(
            conn, "synthetic_labels",
            data_path / "labels.csv",
            ["txn_id", "is_fraud", "fraud_type"]
        )

        with conn.cursor() as cur:
            cur.execute("SELECT count(*) FROM transactions;")
            txn_count = cur.fetchone()[0]
            print(f"\nVerification: Total transactions in database = {txn_count:,}")

        return txn_count
    finally:
        conn.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Bulk load CSV data into PostgreSQL")
    parser.add_argument("--data-dir", default="data", help="Directory containing CSV files")
    parser.add_argument("--host", default=None, help="PostgreSQL host")
    parser.add_argument("--port", default=None, help="PostgreSQL port")
    parser.add_argument("--db", default=None, help="PostgreSQL database name")
    parser.add_argument("--user", default=None, help="PostgreSQL username")
    parser.add_argument("--password", default=None, help="PostgreSQL password")
    args = parser.parse_args()

    load_all_data(
        data_dir=args.data_dir,
        host=args.host,
        port=args.port,
        dbname=args.db,
        user=args.user,
        password=args.password
    )
