"""
Uploads trained models and evaluation reports to AWS S3 or LocalStack S3.
Supports versioned keys and custom endpoint overrides for local testing.
"""

import os
import sys
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Optional

# Ensure root directory is in sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

def upload_artifacts_to_s3(
    bucket_name: Optional[str] = None,
    model_path: str = "ml/isolation_forest.joblib",
    reports_dir: str = "ml/reports",
    endpoint_url: Optional[str] = None,
    region_name: str = "us-east-1"
) -> Dict[str, List[str]]:
    import boto3
    from botocore.exceptions import ClientError

    bucket = bucket_name or os.environ.get("AWS_S3_BUCKET", "risk-monitoring-bucket")
    endpoint = endpoint_url or os.environ.get("AWS_ENDPOINT_URL", None)
    region = os.environ.get("AWS_REGION", region_name)

    client_kwargs = {"region_name": region}
    if endpoint:
        client_kwargs["endpoint_url"] = endpoint

    s3 = boto3.client("s3", **client_kwargs)

    # Ensure bucket exists
    try:
        s3.head_bucket(Bucket=bucket)
    except ClientError:
        try:
            s3.create_bucket(Bucket=bucket)
            print(f"Created S3 bucket '{bucket}'")
        except Exception as e:
            print(f"Warning: Could not create bucket '{bucket}': {e}")

    uploaded_models = []
    uploaded_reports = []
    timestamp = datetime.utcnow().strftime("%Y%m%d_%H%M%S")

    # Upload model
    model_file = Path(model_path)
    if model_file.exists():
        versioned_key = f"models/{timestamp}_{model_file.name}"
        latest_key = f"models/{model_file.name}"

        s3.upload_file(str(model_file), bucket, versioned_key)
        s3.upload_file(str(model_file), bucket, latest_key)
        uploaded_models.extend([versioned_key, latest_key])
        print(f"Uploaded model to s3://{bucket}/{latest_key} (and {versioned_key})")

    # Upload reports
    rep_dir = Path(reports_dir)
    if rep_dir.exists():
        for f in rep_dir.iterdir():
            if f.is_file() and not f.name.startswith("."):
                versioned_key = f"reports/{timestamp}_{f.name}"
                latest_key = f"reports/{f.name}"

                s3.upload_file(str(f), bucket, versioned_key)
                s3.upload_file(str(f), bucket, latest_key)
                uploaded_reports.extend([versioned_key, latest_key])
                print(f"Uploaded report to s3://{bucket}/{latest_key}")

    return {
        "models": uploaded_models,
        "reports": uploaded_reports
    }

if __name__ == "__main__":
    upload_artifacts_to_s3()
