from unittest.mock import MagicMock, patch
from ml.upload_to_s3 import upload_artifacts_to_s3
from pathlib import Path

def test_upload_artifacts_to_s3(tmp_path):
    mock_s3 = MagicMock()

    # Create dummy model and report files
    model_file = tmp_path / "model.joblib"
    model_file.write_text("dummy model binary")

    reports_dir = tmp_path / "reports"
    reports_dir.mkdir()
    (reports_dir / "report1.json").write_text("{\"metric\": 0.95}")

    with patch("boto3.client", return_value=mock_s3):
        result = upload_artifacts_to_s3(
            bucket_name="test-bucket",
            model_path=str(model_file),
            reports_dir=str(reports_dir),
            endpoint_url="http://localhost:4566",
            region_name="us-east-1"
        )

        assert len(result["models"]) == 2 # versioned and latest
        assert len(result["reports"]) == 2 # versioned and latest
        assert any(k.endswith("model.joblib") for k in result["models"])
        assert any(k.endswith("report1.json") for k in result["reports"])

        # Verify S3 upload calls
        assert mock_s3.upload_file.call_count == 4

