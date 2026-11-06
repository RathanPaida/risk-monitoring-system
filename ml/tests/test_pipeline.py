import pytest
import pandas as pd
import numpy as np
from pathlib import Path
from unittest.mock import MagicMock, patch
from ml.generate_data import generate_dataset
from ml.train_isolation_forest import train_model
from ml.evaluate import run_evaluation, generate_plots, write_markdown_report, calculate_metrics
from ml.load_data import load_table

def test_train_model_small(tmp_path):
    """Test training isolation forest on scaled-down generated data."""
    data_dir = tmp_path / "data"
    generate_dataset(
        output_dir=str(data_dir),
        seed=123,
        num_customers=40,
        num_merchants=15,
        num_transactions=500,
        days=10
    )

    model_path = tmp_path / "isolation_forest.joblib"
    result = train_model(
        data_dir=str(data_dir),
        model_output_path=str(model_path),
        train_ratio=0.7,
        contamination=0.02
    )

    assert result["model"] is not None
    assert model_path.exists()
    assert len(result["test_txn_ids"]) > 0

def test_load_table_dry_run(tmp_path):
    """Test load_table with a mock cursor/connection."""
    csv_file = tmp_path / "sample.csv"
    pd.DataFrame({"id": [1, 2], "val": ["a", "b"]}).to_csv(csv_file, index=False)

    mock_conn = MagicMock()
    mock_cursor = MagicMock()
    mock_cursor.connection.encoding = "UTF8"
    mock_cursor.mogrify.return_value = b"(1, 'a')"
    mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

    rows = load_table(mock_conn, "test_table", csv_file, ["id", "val"], batch_size=2)
    assert rows == 2
    mock_conn.commit.assert_called_once()

    # Test non-existent file returns 0
    non_file = tmp_path / "does_not_exist.csv"
    assert load_table(mock_conn, "test_table", non_file, ["id"]) == 0

def test_run_evaluation_mocked(tmp_path):
    """Test end-to-end evaluate workflow with mocked DB connection."""
    data_dir = tmp_path / "data"
    generate_dataset(
        output_dir=str(data_dir),
        seed=42,
        num_customers=30,
        num_merchants=10,
        num_transactions=400,
        days=8
    )

    model_path = tmp_path / "model.joblib"
    train_model(
        data_dir=str(data_dir),
        model_output_path=str(model_path),
        train_ratio=0.7,
        contamination=0.02
    )

    reports_dir = tmp_path / "reports"
    with patch("ml.evaluate.get_db_rule_alerts", return_value={1, 2, 5, 10}):
        res = run_evaluation(
            data_dir=str(data_dir),
            model_path=str(model_path),
            reports_dir=str(reports_dir),
            train_ratio=0.7
        )

    assert "rules" in res
    assert "ml" in res
    assert "combined" in res
    assert (reports_dir / "evaluation.md").exists()
    assert (reports_dir / "metrics_comparison.png").exists()
    assert (reports_dir / "workload_tradeoff.png").exists()
