# Release Notes - v1.0.0

## Transaction Risk Monitoring & Alerting System

**Release Date:** November 6, 2026  
**Git Tag:** `v1.0.0`

### 1. Overview
Version 1.0.0 is the initial production release of the Transaction Risk Monitoring & Alerting System, a high-performance banking risk platform designed to ingest high-volume transactions (100K+ records), detect fraud through deterministic rules and unsupervised anomaly detection, and route alerts through a role-based review workflow backed by an immutable audit trail.

---

### 2. Key Features Included

- **High-Volume Transaction Ingestion & Storage:**
  - Ingestion of 105,000 realistic synthetic financial transactions across 90 days.
  - Realistic fraud injection across 5 fraud patterns (velocity burst, amount spike, foreign country hop, new device large transaction, subtle anomaly).
  - PostgreSQL schema with Flyway migrations (`V1`-`V3`) and composite indexes (`(account_id, txn_time)`).

- **Deterministic Rule Engine:**
  - 4 configurable rules: `VelocityRule`, `UnusualAmountRule`, `GeoMismatchRule`, `NewDeviceRule`.
  - In-memory `FastReplayContext` executing historical stream replays in **973 ms** for 105,000 transactions, generating 4,019 alerts.

- **Role-Based Alert Workflow & Security:**
  - Finite State Machine: `OPEN` $\rightarrow$ `IN_REVIEW` $\rightarrow$ `ESCALATED` $\rightarrow$ `CLOSED_CONFIRMED` / `CLOSED_FALSE_POSITIVE`.
  - Spring Security with role separation between `ROLE_ANALYST` and `ROLE_MANAGER`.
  - PostgreSQL engine-level trigger (`V2__audit_immutable.sql`) blocking `UPDATE` and `DELETE` operations on `audit_log`.

- **Unsupervised Machine Learning & Evaluation:**
  - 10-feature extraction pipeline (`ml/features.py`) strictly avoiding target leakage.
  - Scikit-learn `IsolationForest` trained on a strict 70-day time-split.
  - Out-of-time evaluation (`ml/evaluate.py`) benchmarking Rules (33.4% precision, 64.9% recall) vs ML (27.1% precision, 40.6% recall) vs Combined (67.4% recall).
  - Generated visual trade-off analysis charts and technical documentation.

- **Quality, DevOps & Testing:**
  - 83.9% JaCoCo line test coverage on Java backend (71 passing tests).
  - 93.1% Python test coverage on ML pipeline (9 passing tests).
  - Multi-stage Docker container build and Docker Compose configuration.
  - Automated GitHub Actions CI workflow with JDK 17, Python 3.11, and Docker build jobs.

---

### 3. Verification & Acceptance
All 12 items in Section 10 of `PLAN.md` have been verified.
