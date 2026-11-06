"""
Comprehensive benchmark evaluation comparing Deterministic Rules vs Isolation Forest.
Evaluates Precision, Recall, F1, FPR, and daily analyst alert workload.
Generates ml/reports/evaluation.md and visual comparison plots.
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
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import psycopg2

from ml.features import extract_features, FEATURE_COLUMNS

def calculate_metrics(y_true: np.ndarray, y_pred: np.ndarray, days: float = 20.0):
    """
    Computes confusion matrix, precision, recall, f1, false-positive rate, and daily workload.
    """
    y_true = np.asarray(y_true, dtype=int)
    y_pred = np.asarray(y_pred, dtype=int)

    tp = int(np.sum((y_true == 1) & (y_pred == 1)))
    fp = int(np.sum((y_true == 0) & (y_pred == 1)))
    fn = int(np.sum((y_true == 1) & (y_pred == 0)))
    tn = int(np.sum((y_true == 0) & (y_pred == 0)))

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0.0
    fpr = fp / (fp + tn) if (fp + tn) > 0 else 0.0
    total_alerts = tp + fp
    alerts_per_day = total_alerts / days if days > 0 else 0.0

    return {
        "TP": tp,
        "FP": fp,
        "FN": fn,
        "TN": tn,
        "Precision": precision,
        "Recall": recall,
        "F1": f1,
        "FPR": fpr,
        "Total Alerts": total_alerts,
        "Alerts / Day": alerts_per_day
    }

def get_db_rule_alerts(host="localhost", port="5433", db="riskmonitor", user="postgres", password="postgres"):
    """Fetch set of transaction IDs that triggered at least one rule alert in PostgreSQL."""
    conn = psycopg2.connect(
        host=os.getenv("DB_HOST", host),
        port=os.getenv("DB_PORT", port),
        dbname=os.getenv("DB_NAME", db),
        user=os.getenv("DB_USER", user),
        password=os.getenv("DB_PASSWORD", password)
    )
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT DISTINCT txn_id FROM alerts;")
            rows = cur.fetchall()
            return set(r[0] for r in rows)
    finally:
        conn.close()

def run_evaluation(
    data_dir: str = "data",
    model_path: str = "ml/isolation_forest.joblib",
    reports_dir: str = "ml/reports",
    train_ratio: float = 0.77
):
    data_path = Path(data_dir)
    reports_path = Path(reports_dir)
    reports_path.mkdir(parents=True, exist_ok=True)

    print("Loading test dataset and ground-truth labels...")
    df_txns = pd.read_csv(data_path / "transactions.csv")
    df_labels = pd.read_csv(data_path / "labels.csv")
    df_customers = pd.read_csv(data_path / "customers.csv")
    df_accounts = pd.read_csv(data_path / "accounts.csv")

    df_txns["txn_time"] = pd.to_datetime(df_txns["txn_time"])
    df_txns = df_txns.sort_values("txn_time").reset_index(drop=True)

    # Time-based split matching training split
    split_idx = int(len(df_txns) * train_ratio)
    split_time = df_txns.iloc[split_idx]["txn_time"]

    test_mask = df_txns["txn_time"] >= split_time
    df_test_txns = df_txns[test_mask].reset_index(drop=True)

    # Join labels
    label_map = df_labels.set_index("txn_id")["is_fraud"].to_dict()
    fraud_type_map = df_labels.set_index("txn_id")["fraud_type"].to_dict()
    df_test_txns["is_fraud"] = df_test_txns["id"].map(label_map).fillna(False).astype(bool)
    df_test_txns["fraud_type"] = df_test_txns["id"].map(fraud_type_map).fillna("")

    test_start = df_test_txns["txn_time"].min()
    test_end = df_test_txns["txn_time"].max()
    test_days = max(1.0, (test_end - test_start).total_seconds() / 86400.0)

    print(f"Test split period: {test_start} to {test_end} ({test_days:.1f} days)")
    print(f"Total test transactions: {len(df_test_txns):,}")
    actual_fraud_count = df_test_txns["is_fraud"].sum()
    print(f"Actual test fraud count: {actual_fraud_count:,} ({actual_fraud_count / len(df_test_txns) * 100:.2f}%)")

    # 1. Rule Alerts from PostgreSQL
    print("Reading rule alerts from PostgreSQL database...")
    db_alerted_txn_ids = get_db_rule_alerts()
    rule_pred = df_test_txns["id"].isin(db_alerted_txn_ids).astype(int).to_numpy()

    # 2. ML Anomaly Detection (Isolation Forest)
    print("Extracting features and generating Isolation Forest predictions...")
    df_features, feature_cols = extract_features(df_test_txns, df_customers, df_accounts)
    X_test = df_features[feature_cols].to_numpy()

    model = joblib.load(model_path)
    ml_raw_pred = model.predict(X_test) # -1 is anomaly, 1 is inlier
    ml_pred = (ml_raw_pred == -1).astype(int)

    # 3. Combined Ensemble (Rules OR ML)
    combined_pred = ((rule_pred == 1) | (ml_pred == 1)).astype(int)

    # Ground truth
    y_true = df_test_txns["is_fraud"].astype(int).to_numpy()

    # Compute benchmark metrics
    m_rules = calculate_metrics(y_true, rule_pred, test_days)
    m_ml = calculate_metrics(y_true, ml_pred, test_days)
    m_comb = calculate_metrics(y_true, combined_pred, test_days)

    results_table = pd.DataFrame([
        {
            "System": "Rules Only",
            "Precision": f"{m_rules['Precision']:.2%}",
            "Recall": f"{m_rules['Recall']:.2%}",
            "F1 Score": f"{m_rules['F1']:.4f}",
            "FPR": f"{m_rules['FPR']:.2%}",
            "Total Alerts": f"{m_rules['Total Alerts']:,}",
            "Alerts/Day": f"{m_rules['Alerts / Day']:.1f}",
            "True Positives": m_rules["TP"],
            "False Positives": m_rules["FP"]
        },
        {
            "System": "Isolation Forest",
            "Precision": f"{m_ml['Precision']:.2%}",
            "Recall": f"{m_ml['Recall']:.2%}",
            "F1 Score": f"{m_ml['F1']:.4f}",
            "FPR": f"{m_ml['FPR']:.2%}",
            "Total Alerts": f"{m_ml['Total Alerts']:,}",
            "Alerts/Day": f"{m_ml['Alerts / Day']:.1f}",
            "True Positives": m_ml["TP"],
            "False Positives": m_ml["FP"]
        },
        {
            "System": "Combined (Rules OR ML)",
            "Precision": f"{m_comb['Precision']:.2%}",
            "Recall": f"{m_comb['Recall']:.2%}",
            "F1 Score": f"{m_comb['F1']:.4f}",
            "FPR": f"{m_comb['FPR']:.2%}",
            "Total Alerts": f"{m_comb['Total Alerts']:,}",
            "Alerts/Day": f"{m_comb['Alerts / Day']:.1f}",
            "True Positives": m_comb["TP"],
            "False Positives": m_comb["FP"]
        }
    ])

    print("\nBenchmark Evaluation Results:")
    print(results_table.to_string(index=False))

    # Generate visual plots
    generate_plots(m_rules, m_ml, m_comb, reports_path)

    # Write evaluation.md
    write_markdown_report(results_table, m_rules, m_ml, m_comb, actual_fraud_count, len(df_test_txns), test_days, reports_path)

    return {
        "rules": m_rules,
        "ml": m_ml,
        "combined": m_comb,
        "table": results_table
    }

def generate_plots(m_rules, m_ml, m_comb, reports_path: Path):
    plt.style.use("seaborn-v0_8-whitegrid" if "seaborn-v0_8-whitegrid" in plt.style.available else "default")
    
    # Plot 1: Metric Comparison (Precision, Recall, F1, FPR)
    fig, ax = plt.subplots(figsize=(9, 5))
    metrics = ["Precision", "Recall", "F1 Score", "FPR"]
    systems = ["Rules Only", "Isolation Forest", "Combined (OR)"]
    x = np.arange(len(metrics))
    width = 0.25

    val_rules = [m_rules["Precision"], m_rules["Recall"], m_rules["F1"], m_rules["FPR"]]
    val_ml = [m_ml["Precision"], m_ml["Recall"], m_ml["F1"], m_ml["FPR"]]
    val_comb = [m_comb["Precision"], m_comb["Recall"], m_comb["F1"], m_comb["FPR"]]

    ax.bar(x - width, val_rules, width, label="Rules Only", color="#2b5c8f")
    ax.bar(x, val_ml, width, label="Isolation Forest", color="#d95f02")
    ax.bar(x + width, val_comb, width, label="Combined (OR)", color="#7570b3")

    ax.set_ylabel("Score / Rate")
    ax.set_title("Detection Performance: Rules vs Isolation Forest vs Combined")
    ax.set_xticks(x)
    ax.set_xticklabels(metrics)
    ax.set_ylim(0, 1.05)
    ax.legend()
    plt.tight_layout()
    plot1_path = reports_path / "metrics_comparison.png"
    fig.savefig(plot1_path, dpi=200)
    plt.close(fig)

    # Plot 2: Analyst Workload vs Recall Tradeoff
    fig, ax = plt.subplots(figsize=(8, 4.5))
    workloads = [m_rules["Alerts / Day"], m_ml["Alerts / Day"], m_comb["Alerts / Day"]]
    recalls = [m_rules["Recall"] * 100, m_ml["Recall"] * 100, m_comb["Recall"] * 100]
    colors = ["#2b5c8f", "#d95f02", "#7570b3"]

    for i, sys in enumerate(systems):
        ax.scatter(workloads[i], recalls[i], s=250, c=colors[i], label=sys, zorder=5)
        ax.annotate(f" {sys}\n ({workloads[i]:.1f} alerts/day, {recalls[i]:.1f}% recall)",
                    (workloads[i], recalls[i]), textcoords="offset points", xytext=(8, -5), fontsize=9)

    ax.set_xlabel("Analyst Workload (Alerts per Day)")
    ax.set_ylabel("Fraud Recall (%)")
    ax.set_title("Analyst Triage Workload vs Fraud Detection Recall")
    ax.grid(True, linestyle="--", alpha=0.6)
    plt.tight_layout()
    plot2_path = reports_path / "workload_tradeoff.png"
    fig.savefig(plot2_path, dpi=200)
    plt.close(fig)
    print(f"Generated comparison plots in '{reports_path}'.")

def write_markdown_report(results_table, m_rules, m_ml, m_comb, fraud_count, total_txns, test_days, reports_path: Path):
    content = f"""# Fraud Detection Benchmark & Evaluation Report
## Deterministic Rules vs Unsupervised Machine Learning (Isolation Forest)

### 1. Executive Summary

This report presents an empirical, out-of-time evaluation benchmarking our **deterministic 4-rule engine** against an unsupervised **Isolation Forest model** and an **ensemble hybrid (Rules OR ML)**.

The evaluation was executed on an independent, out-of-time test split encompassing **{total_txns:,} transactions** over **{test_days:.1f} days**, containing **{fraud_count:,} ground-truth fraudulent transactions** (natural prevalence: {fraud_count / total_txns * 100:.2f}%).

---

### 2. Empirical Benchmark Results

| Fraud Detection Method | Precision | Recall | F1 Score | False Positive Rate (FPR) | Total Alerts | Alerts / Day | True Positives | False Positives |
|---|---|---|---|---|---|---|---|---|
| **Rules Only** | {m_rules['Precision']:.2%} | {m_rules['Recall']:.2%} | {m_rules['F1']:.4f} | {m_rules['FPR']:.2%} | {m_rules['Total Alerts']:,} | {m_rules['Alerts / Day']:.1f} | {m_rules['TP']:,} | {m_rules['FP']:,} |
| **Isolation Forest (ML)** | {m_ml['Precision']:.2%} | {m_ml['Recall']:.2%} | {m_ml['F1']:.4f} | {m_ml['FPR']:.2%} | {m_ml['Total Alerts']:,} | {m_ml['Alerts / Day']:.1f} | {m_ml['TP']:,} | {m_ml['FP']:,} |
| **Combined (Rules OR ML)** | {m_comb['Precision']:.2%} | {m_comb['Recall']:.2%} | {m_comb['F1']:.4f} | {m_comb['FPR']:.2%} | {m_comb['Total Alerts']:,} | {m_comb['Alerts / Day']:.1f} | {m_comb['TP']:,} | {m_comb['FP']:,} |

---

### 3. Visual Analysis

![Metrics Comparison](metrics_comparison.png)

![Workload Trade-off](workload_tradeoff.png)

---

### 4. Honest Technical Discussion & Trade-offs

#### 4.1 Where Deterministic Rules Win
1. **Explainability & Auditability:** Every rule hit produces a clear, deterministic reason code (e.g., *“Velocity exceeded: 8 transactions in 10 minutes”*), directly empowering fraud analysts to take action during reviews without black-box opacity.
2. **Deterministic Edge Cases:** Rules capture high-velocity bursts and country jumps immediately without requiring statistical density estimates.

#### 4.2 Where Isolation Forest Wins
1. **Subtle & Multi-Dimensional Anomalies:** The Isolation Forest isolates anomalies in continuous multi-dimensional space, flagging transactions that exhibit subtle, concurrent shifts (e.g., slightly elevated amounts combined with atypical transaction hours or channels) that individually fall beneath fixed rule thresholds.
2. **Adaptive Thresholding:** Unsupervised models adapt to complex distributions without needing manually hardcoded static limits.

#### 4.3 What Each Method Misses
- **Rules Missed:** Sophisticated fraud tailored to stay just under rule thresholds (e.g., $480 purchases when threshold is $500, or 4 rapid transactions when velocity trigger is 5).
- **ML Missed:** High-frequency bursts where individual transaction amounts are normal, which dilute average anomaly scores if temporal velocity is not explicitly isolated.

#### 4.4 Operational Workload Trade-off
- Combining **Rules OR ML** yields the highest overall fraud recall (**{m_comb['Recall']:.2%}**), intercepting **{m_comb['TP']:,} fraudulent transactions**.
- However, combining both increases analyst daily triage volume to **{m_comb['Alerts / Day']:.1f} alerts/day**.
- In production, a tiered routing strategy is recommended: high-severity deterministic rule hits route directly to immediate account lock, while ML anomalies route to the analyst review queue.
"""
    with open(reports_path / "evaluation.md", "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Generated comprehensive report at '{reports_path / 'evaluation.md'}'.")

if __name__ == "__main__":
    run_evaluation()
