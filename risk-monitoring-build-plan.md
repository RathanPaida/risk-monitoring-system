# Transaction Risk Monitoring & Alerting System: Build Plan

Goal: make the repo match every claim on the resume.

**Target resume bullets**

- Spring Boot REST service with PostgreSQL ingesting 100K+ synthetic transactions
- 4 fraud rules (velocity, unusual amount, geo-mismatch, new device)
- Alert state machine with escalation workflow
- Role-based access (analyst vs. manager)
- Immutable audit log recording every alert action
- Scheduled ingestion from AWS S3 using a multi-threaded worker pool
- DynamoDB conditional writes preventing duplicate processing
- Trained model and reports stored in S3
- Isolation Forest (Python) compared with the rule engine on precision, recall, false-positive rate
- SDLC workflow (requirements, user stories, pull requests)
- JUnit/pytest at 80%+ coverage, GitHub Actions CI, Docker

**Submission rule:** only submit a resume once every bullet on it is true of the repo on `main`.

- Wells version: submit after Phase 3.
- Amazon version: submit after Phase 4.

---

## Phase 0: Audit and SDLC setup (about 15 to 30 min)

- [x] List every resume claim and mark it done / partial / missing against the current repo.
- [x] Create `docs/requirements.md` with functional and non-functional requirements.
- [x] Create GitHub Issues as user stories, one per feature below.
  - Example: "As an analyst, I can move an alert to UNDER_REVIEW so that my team knows it is being handled."
- [x] Work on one branch per feature and merge through pull requests, even when working solo.
- [x] Add a PR template (`.github/pull_request_template.md`) with a short checklist: tests added, docs updated.

---

## Phase 1: Wells core (Java, Spring Boot, PostgreSQL)

### 1. Data model and migrations

- [x] Add Flyway.
- [x] Tables: `users`, `transactions`, `alerts`, `alert_audit_log`.
- [x] Indexes on `transactions(account_id, timestamp)` and `transactions(device_id)`.
- [x] Unique constraint on `transactions.txn_id` (used for idempotency later).

### 2. Synthetic data generator (100K+ rows)

- [x] Generate 100K+ transactions with accounts, merchants, locations, devices, amounts and timestamps.
- [x] Inject fraud patterns with a ground-truth `is_fraud` label that is **not** derived from your rule logic.
- [x] Include fraud types the rules cannot easily catch (gradual amount drift, odd-hour spending) so the ML comparison is meaningful.
- [x] Make generation seedable and reproducible.
- [x] Add an endpoint or CLI command to load the data.

### 3. Four fraud rules

- [x] `Rule` interface, one class per rule, and a `RuleEngine` that runs all rules per transaction.
- [x] **Velocity:** more than N transactions in M minutes per account.
- [x] **Unusual amount:** amount above k standard deviations (or a percentile) of the account's history.
- [x] **Geo-mismatch:** impossible travel (distance versus elapsed time) or a new country.
- [x] **New device:** device ID never seen for this account.
- [x] Make thresholds configurable in `application.yml`.
- [x] Each triggered rule creates an alert with a reason code.

### 4. Alert state machine and escalation

- [x] States: `NEW -> UNDER_REVIEW -> ESCALATED -> CONFIRMED_FRAUD | FALSE_POSITIVE -> CLOSED`.
- [x] Explicit allowed-transitions map; illegal transitions return HTTP 409.
- [x] `@Scheduled` job escalates alerts left unreviewed past an SLA.
- [x] Manual escalate endpoint.
- [x] REST endpoints: list alerts (filter by status), get alert, transition alert.

### 5. Role-based access

- [x] Spring Security with roles `ANALYST` and `MANAGER`.
- [x] Analyst: review, escalate.
- [x] Manager: everything an analyst can do, plus confirm, close and view reports.
- [x] Enforce with `@PreAuthorize` or a security config.
- [x] Seed one analyst user and one manager user (Flyway or startup runner).

### 6. Immutable audit log

- [x] Every alert action writes a row: actor, action, old state, new state, timestamp.
- [x] Postgres trigger that raises an exception on `UPDATE` or `DELETE` of `alert_audit_log`.
- [x] Optional: revoke `UPDATE` and `DELETE` from the application DB user.
- [x] Optional: hash chain (each row stores the hash of the previous row) and a verify endpoint.
- [x] Endpoint to read the audit trail for an alert (manager only).

---

## Phase 2: Python Isolation Forest vs. rule engine

- [x] Create the `ml/` directory with `train.py`, `evaluate.py`, `features.py`, `requirements.txt`.
- [x] Export transactions from Postgres to CSV or Parquet.
- [x] Features: amount z-score, transactions in the last hour, distance from the previous transaction, new-device flag, hour of day.
- [x] Train/test split by time (not random) to avoid leakage.
- [x] Train `IsolationForest`; tune `contamination` and threshold on the training split only.
- [x] Evaluate **both** the model and the rule engine on the same held-out test set against `is_fraud`.
- [x] Compute precision, recall and false-positive rate for each.
- [x] Put the results table and a short written analysis in the README (include honest findings, for example where each approach wins).
- [x] Save `model.joblib` and a `report.json` / `report.md`.
- [x] pytest tests for feature engineering and the metric calculations.

---

## Phase 3: Tests, CI, Docker (then submit the Wells resume)

- [x] **JUnit 5 and Mockito** unit tests: each rule, state machine transitions, RBAC.
- [x] **Testcontainers (Postgres)** integration tests for repositories, the audit trigger and endpoints.
- [x] **JaCoCo** with a build rule that fails below 80% coverage.
- [x] **pytest-cov** with `--cov-fail-under=80`.
- [x] **GitHub Actions** workflow on PRs and pushes to `main`:
  - [x] `mvn verify` (tests and coverage check)
  - [x] `pytest` with coverage
  - [x] `docker build`
- [x] **Dockerfile** (multi-stage) for the Spring Boot app.
- [x] **docker-compose.yml** with the app and Postgres.
- [x] README: architecture, setup (`docker compose up`), API examples, results table, CI badge.
- [x] Merge to `main` and tag `v1.0-core`.

### Checkpoint before submitting Wells

- [x] `SELECT count(*) FROM transactions;` is above 100,000.
- [x] The coverage reports show 80%+ for both Java and Python.
- [x] CI is green on `main`.
- [x] README matches what exists (no AWS mentions yet).

---

## Phase 4: AWS version (branch `feature/aws`)

Develop against **LocalStack** in docker-compose. Do a final run against real AWS (free tier) so the "AWS" claim is truthful.

### 7. S3 scheduled ingestion

- [x] Add AWS SDK v2 dependencies (S3, DynamoDB).
- [x] `@Scheduled` job lists new objects under `incoming/`.
- [x] Parse CSV/JSON batch files into transactions.
- [x] Move files to `processed/` on success and `failed/` on error.
- [x] Configurable bucket, prefix and poll interval.

### 8. Multi-threaded worker pool

- [x] Fixed-size `ExecutorService` (configurable pool size).
- [x] Submit files (or chunks of large files) as tasks.
- [x] Batched JDBC inserts.
- [x] Per-task error handling, so one failure does not stop other tasks.
- [x] Graceful shutdown (`@PreDestroy`).
- [x] Idempotent inserts: `INSERT ... ON CONFLICT (txn_id) DO NOTHING`.

### 9. DynamoDB conditional writes (deduplication)

- [x] Table `ingestion_jobs`, partition key `batch_key` (S3 key plus ETag).
- [x] Before processing, call `PutItem` with `ConditionExpression: attribute_not_exists(batch_key)`, status `IN_PROGRESS`, plus a lease expiry timestamp.
- [x] On `ConditionalCheckFailedException`, skip: another worker owns this batch.
- [x] On success, update status to `DONE`; on failure, update to `FAILED`.
- [x] Reclaim expired leases (crashed worker) using a conditional update.
- [x] Test: two competing workers on the same file, and only one processes it.

### 10. Model and reports in S3

- [x] `train.py` uploads `model.joblib` and the report to `s3://<bucket>/models/` and `/reports/` with versioned keys (`boto3`).
- [x] Optional: the Spring app exposes a manager-only endpoint listing reports.

### 11. Tests, CI, docs

- [x] LocalStack integration tests (Testcontainers) for S3 ingestion and DynamoDB locking.
- [x] Keep coverage at 80%+.
- [x] Add LocalStack to docker-compose and CI.
- [x] Update README: architecture diagram with S3, DynamoDB and worker pool; configuration for real AWS.
- [x] Merge to `main` and tag `v2.0-aws`.

### Checkpoint before submitting Amazon

- [x] Drop a batch file in S3, and it is ingested on schedule.
- [x] Dropping the same file twice (or running two instances) processes it only once.
- [x] The model and report are visible in S3.
- [x] CI is green and the README is accurate.

---

## Phase 5: Final verification and interview prep

- [x] Every resume number is measured, not assumed (100K+, 80%+, 4 rules, 4 roles/permissions).
- [x] GitHub repo is public with a clear README and a clean commit history.
- [x] Be ready to explain:
  - [x] Why a state machine with an explicit transitions map
  - [x] How the audit log is made immutable
  - [x] How conditional writes prevent duplicate processing, and what happens when a worker crashes
  - [x] Why a thread pool, and how you handle failures
  - [x] Why the ML comparison is not circular (labels are independent of the rules)
  - [x] Where Isolation Forest beats the rules and where it does not

---

## Suggested order and time estimate

| Phase | Focus | Estimate |
|-------|-------|----------|
| 0 | Audit and SDLC setup | 0.5 hr |
| 1 | Core service (steps 1 to 6) | 1 to 2 days |
| 2 | Isolation Forest comparison | 0.5 to 1 day |
| 3 | Tests, CI, Docker | 0.5 to 1 day |
| 4 | AWS integration (steps 7 to 11) | 1 to 2 days |
| 5 | Verification and prep | 0.5 day |
