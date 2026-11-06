import pytest
import numpy as np
from ml.evaluate import calculate_metrics

def test_calculate_metrics_hand_crafted():
    # Ground truth: 3 frauds (indices 0, 1, 4), 2 normal (indices 2, 3)
    y_true = np.array([1, 1, 0, 0, 1])
    # Predictions: flagged 0, 3, 4
    y_pred = np.array([1, 0, 0, 1, 1])

    # TP: (0, 4) -> 2
    # FP: (3) -> 1
    # FN: (1) -> 1
    # TN: (2) -> 1
    metrics = calculate_metrics(y_true, y_pred, days=2.0)

    assert metrics["TP"] == 2
    assert metrics["FP"] == 1
    assert metrics["FN"] == 1
    assert metrics["TN"] == 1

    assert pytest.approx(metrics["Precision"], 0.001) == 2.0 / 3.0 # 0.6667
    assert pytest.approx(metrics["Recall"], 0.001) == 2.0 / 3.0    # 0.6667
    assert pytest.approx(metrics["F1"], 0.001) == 2.0 / 3.0        # 0.6667
    assert pytest.approx(metrics["FPR"], 0.001) == 1.0 / 2.0       # 0.5000
    assert metrics["Total Alerts"] == 3
    assert pytest.approx(metrics["Alerts / Day"], 0.001) == 1.5

def test_calculate_metrics_zero_alerts():
    y_true = np.array([1, 0, 1, 0])
    y_pred = np.array([0, 0, 0, 0])

    metrics = calculate_metrics(y_true, y_pred, days=1.0)

    assert metrics["TP"] == 0
    assert metrics["FP"] == 0
    assert metrics["Precision"] == 0.0
    assert metrics["Recall"] == 0.0
    assert metrics["F1"] == 0.0
    assert metrics["FPR"] == 0.0
    assert metrics["Total Alerts"] == 0
