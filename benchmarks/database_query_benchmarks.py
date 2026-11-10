"""
PostgreSQL Query Latency & Index Benchmark
Executes EXPLAIN (ANALYZE, BUFFERS) on key production queries at 100K+ rows.
Measures execution time, planning time, buffer hit/read counts, and execution plan type
BEFORE and AFTER adding missing indexes.
"""

import json
import psycopg2
from psycopg2.extras import RealDictCursor

QUERIES = [
    {
        "name": "Q1: Alerts by Status ordered by Date (Analyst triage queue)",
        "sql": "SELECT * FROM alerts WHERE status = 'OPEN' ORDER BY created_at DESC LIMIT 50;",
        "index_sql": "CREATE INDEX idx_alerts_status_created ON alerts (status, created_at DESC);",
        "drop_sql": "DROP INDEX IF EXISTS idx_alerts_status_created;"
    },
    {
        "name": "Q2: Alerts by Date Range (Audit and compliance reporting)",
        "sql": "SELECT * FROM alerts WHERE created_at >= NOW() - INTERVAL '30 days' ORDER BY created_at DESC LIMIT 50;",
        "index_sql": "CREATE INDEX idx_alerts_created_at ON alerts (created_at DESC);",
        "drop_sql": "DROP INDEX IF EXISTS idx_alerts_created_at;"
    },
    {
        "name": "Q3: Transactions by Merchant and Date Range (Merchant risk assessment)",
        "sql": "SELECT * FROM transactions WHERE merchant_id = 12 AND txn_time >= '2026-08-01' AND txn_time <= '2026-10-31';",
        "index_sql": "CREATE INDEX idx_transactions_merchant_time ON transactions (merchant_id, txn_time);",
        "drop_sql": "DROP INDEX IF EXISTS idx_transactions_merchant_time;"
    },
    {
        "name": "Q4: Alerts by Account (Customer transaction history review)",
        "sql": "SELECT a.* FROM alerts a JOIN transactions t ON a.txn_id = t.id WHERE t.account_id = 101 ORDER BY a.created_at DESC;",
        "index_sql": "CREATE INDEX idx_alerts_txn_created ON alerts (txn_id, created_at DESC);",
        "drop_sql": "DROP INDEX IF EXISTS idx_alerts_txn_created;"
    }
]

def run_explain_analyze(cur, sql, runs=5):
    # Warm-up run
    cur.execute(f"EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) {sql}")
    
    exec_times = []
    plan_types = set()
    shared_hits = []
    shared_reads = []
    
    for _ in range(runs):
        cur.execute(f"EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) {sql}")
        res = cur.fetchone()[0][0]
        exec_times.append(res["Execution Time"])
        
        plan = res["Plan"]
        # Extract main scan node type
        def find_scans(node):
            types = []
            if "Node Type" in node:
                types.append(node["Node Type"])
            for child in node.get("Plans", []):
                types.extend(find_scans(child))
            return types
        plan_types.update(find_scans(plan))
        shared_hits.append(plan.get("Shared Hit Blocks", 0))
        shared_reads.append(plan.get("Shared Read Blocks", 0))
        
    exec_times.sort()
    median_time = exec_times[len(exec_times) // 2]
    min_time = exec_times[0]
    max_time = exec_times[-1]
    
    scan_summary = ", ".join(sorted(plan_types))
    return {
        "median_ms": median_time,
        "min_ms": min_time,
        "max_ms": max_time,
        "plan_types": scan_summary
    }

def main():
    conn = psycopg2.connect(
        host="localhost",
        port=5433,
        user="postgres",
        password="postgres",
        dbname="riskmonitor"
    )
    conn.autocommit = True
    cur = conn.cursor()
    
    results = []
    print("=" * 80)
    print("RUNNING DATABASE QUERY LATENCY BENCHMARKS (105,000+ rows)...")
    print("=" * 80)
    
    for q in QUERIES:
        print(f"\n--- Testing: {q['name']} ---")
        
        # 1. Ensure index is dropped for BEFORE measurement
        cur.execute(q["drop_sql"])
        before = run_explain_analyze(cur, q["sql"])
        print(f"  BEFORE Index: Median = {before['median_ms']:.3f} ms (Min: {before['min_ms']:.3f}, Max: {before['max_ms']:.3f})")
        print(f"                Plan Nodes: {before['plan_types']}")
        
        # 2. Create the index
        print(f"  Creating Index: {q['index_sql']}")
        cur.execute(q["index_sql"])
        
        # 3. Measure AFTER index
        after = run_explain_analyze(cur, q["sql"])
        print(f"  AFTER Index:  Median = {after['median_ms']:.3f} ms (Min: {after['min_ms']:.3f}, Max: {after['max_ms']:.3f})")
        print(f"                Plan Nodes: {after['plan_types']}")
        
        speedup = before['median_ms'] / after['median_ms'] if after['median_ms'] > 0 else 1.0
        print(f"  SPEEDUP:      {speedup:.2f}x")
        
        results.append({
            "name": q["name"],
            "before": before,
            "after": after,
            "speedup": speedup,
            "index_sql": q["index_sql"]
        })
        
        # Keep index or cleanup according to policy
        # We will keep them or save as Flyway migration if justified!
    
    cur.close()
    conn.close()
    
    # Save results to JSON
    with open("benchmarks/query_benchmark_results.json", "w") as f:
        json.dump(results, f, indent=2)
    print("\nResults saved to benchmarks/query_benchmark_results.json")

if __name__ == "__main__":
    main()

