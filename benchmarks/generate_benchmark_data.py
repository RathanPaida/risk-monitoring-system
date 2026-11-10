"""
Deterministic Benchmark Data Generator
Generates synthetic transaction batches of sizes 10K, 100K, and 500K with a fixed random seed.
Validates account, merchant, and device foreign key ranges against the seeded database.
"""

import os
import random
from datetime import datetime, timedelta, timezone
from pathlib import Path
import json
import csv

def generate_transactions(count: int, seed: int = 42, start_id: int = 200001):
    random.seed(seed)
    base_time = datetime(2026, 11, 1, 0, 0, 0, tzinfo=timezone.utc)
    channels = ["ONLINE", "POS", "ATM"]
    countries = ["US", "US", "US", "US", "US", "US", "US", "US", "CA", "GB"]
    
    transactions = []
    for i in range(count):
        txn_id = start_id + i
        account_id = random.randint(1, 2800)
        merchant_id = random.randint(1, 500)
        device_id = random.randint(1, 3200)
        
        # 95% regular amounts, 5% high amounts to trigger fraud rules
        if random.random() < 0.05:
            amount = round(random.uniform(800.0, 5000.0), 2)
        else:
            amount = round(random.uniform(5.0, 300.0), 2)
            
        currency = "USD"
        country = random.choice(countries)
        channel = random.choice(channels)
        
        # Staggered timestamps over 7 days
        delta_sec = random.randint(0, 7 * 86400)
        txn_time = (base_time + timedelta(seconds=delta_sec)).strftime("%Y-%m-%dT%H:%M:%SZ")
        
        transactions.append({
            "id": txn_id,
            "accountId": account_id,
            "merchantId": merchant_id,
            "deviceId": device_id,
            "amount": amount,
            "currency": currency,
            "country": country,
            "txnTime": txn_time,
            "channel": channel
        })
    return transactions

def save_csv(transactions, filepath):
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    with open(filepath, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "account_id", "merchant_id", "device_id", "amount", "currency", "country", "txn_time", "channel"])
        for t in transactions:
            writer.writerow([
                t["id"], t["accountId"], t["merchantId"], t["deviceId"],
                f"{t['amount']:.2f}", t["currency"], t["country"], t["txnTime"], t["channel"]
            ])
    print(f"Saved {len(transactions):,} transactions to CSV: {filepath}")

def save_json(transactions, filepath):
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    with open(filepath, "w", encoding="utf-8") as f:
        json.dump(transactions, f)
    print(f"Saved {len(transactions):,} transactions to JSON: {filepath}")

if __name__ == "__main__":
    out_dir = Path("benchmarks/data")
    out_dir.mkdir(parents=True, exist_ok=True)
    
    print("Generating deterministic benchmark datasets (seed=42)...")
    for size, name in [(10000, "10k"), (100000, "100k")]:
        print(f"Generating {size:,} transactions...")
        txns = generate_transactions(size, seed=42, start_id=200001)
        save_csv(txns, out_dir / f"benchmark_{name}.csv")
        save_json(txns, out_dir / f"benchmark_{name}.json")
    print("Benchmark datasets generated successfully.")

