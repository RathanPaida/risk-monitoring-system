# System Requirements Specification (SRS)
## Transaction Risk Monitoring & Alerting System

### 1. Introduction
The Transaction Risk Monitoring & Alerting System is an enterprise-grade banking risk platform designed to ingest high-volume financial transactions, detect suspicious and fraudulent activities in real-time using deterministic rule engines and unsupervised machine learning (Isolation Forest), and govern alert disposition through a role-based review and escalation workflow supported by an immutable audit trail.

---

### 2. Functional Requirements (FR)

#### FR1: Transaction Ingestion
- **FR1.1**: The system shall expose a RESTful endpoint `POST /api/transactions` to ingest single financial transactions with payload validation (account ID, merchant ID, device ID, amount, currency, channel, transaction time).
- **FR1.2**: The system shall expose a batch ingestion endpoint `POST /api/transactions/bulk` allowing efficient ingestion of transaction batches.
- **FR1.3**: The system shall provide `GET /api/transactions/{id}` to retrieve transaction details.

#### FR2: Real-Time Fraud Rule Engine
- **FR2.1**: The system shall execute four configurable deterministic fraud detection rules on each ingested transaction:
  1. **VelocityRule**: Evaluates if more than $N$ transactions occur on the same account within a configured time window (default: $N > 5$ in 10 minutes $\rightarrow$ MEDIUM; $N > 10$ $\rightarrow$ HIGH).
  2. **UnusualAmountRule**: Evaluates if transaction amount exceeds $K \times$ the account's trailing 30-day average amount and is above a minimum floor (default: $K > 5\times$, min \$500 $\rightarrow$ MEDIUM; $K > 10\times$ $\rightarrow$ HIGH).
  3. **GeoMismatchRule**: Evaluates if the transaction country differs from the customer's registered home country or differs from the previous transaction country within 1 hour ($\rightarrow$ MEDIUM).
  4. **NewDeviceRule**: Evaluates if the transaction device ID is first seen for the account and the amount exceeds a threshold (default: amount > \$200 $\rightarrow$ LOW; amount > \$1,000 $\rightarrow$ MEDIUM).
- **FR2.2**: All rule thresholds shall be dynamically configurable via `application.yml` without code modification.
- **FR2.3**: Queries supporting rule evaluations must utilize database indices `(account_id, txn_time)` and `(device_id)` to prevent table scans.

#### FR3: Alert Generation
- **FR3.1**: Whenever a rule triggers a hit, the `RuleEngine` shall persist an alert record with status `OPEN`, referencing the rule name, transaction ID, severity, and reason description.
- **FR3.2**: For each alert created, a corresponding `CREATED` event must be recorded in the audit log within the same database transaction.

#### FR4: Alert Review & Escalation Workflow
- **FR4.1**: The alert lifecycle shall enforce a finite state machine:
  $$\text{OPEN} \longrightarrow \text{IN\_REVIEW} \longrightarrow \text{ESCALATED} \longrightarrow \{\text{CLOSED\_CONFIRMED}, \text{CLOSED\_FALSE\_POSITIVE}\}$$
  $$\text{IN\_REVIEW} \longrightarrow \{\text{CLOSED\_CONFIRMED}, \text{CLOSED\_FALSE\_POSITIVE}\}$$
- **FR4.2**: Any invalid state transition attempt shall immediately fail and return HTTP 409 Conflict.
- **FR4.3**: Endpoints must support claiming alerts (`POST /api/alerts/{id}/claim`), escalating (`POST /api/alerts/{id}/escalate`), and closing (`POST /api/alerts/{id}/close`).

#### FR5: Role-Based Access Control (RBAC)
- **FR5.1**: The system shall enforce role-based authorization via Spring Security with HTTP Basic authentication for two roles: `ANALYST` and `MANAGER`.
- **FR5.2**: Both `ANALYST` and `MANAGER` can view transactions, view alerts, claim open alerts, escalate alerts in review, and close alerts directly from `IN_REVIEW`.
- **FR5.3**: Only users with the `MANAGER` role can close an `ESCALATED` alert. Analysts attempting to close an escalated alert shall receive HTTP 403 Forbidden.
- **FR5.4**: Only users with the `MANAGER` role can query the audit log (`GET /api/audit`). Analysts attempting to access audit logs shall receive HTTP 403 Forbidden.
- **FR5.5**: Analysts can only view their own assigned alerts or unclaimed open alerts; Managers can view alerts assigned to all analysts.

#### FR6: Immutable Audit Trail
- **FR6.1**: Every lifecycle action (`CREATED`, `CLAIMED`, `COMMENTED`, `ESCALATED`, `CLOSED`) shall be synchronously recorded in `audit_log` with actor username, old status, new status, details, and timestamp.
- **FR6.2**: Immutability shall be enforced at the database layer via a PostgreSQL trigger (`V2__audit_immutable.sql`) that unconditionally raises an exception on any SQL `UPDATE` or `DELETE` statement.

#### FR7: Machine Learning Anomaly Detection
- **FR7.1**: A Python machine learning pipeline shall compute behavioral features from transaction history without target leakage (`is_fraud` is strictly excluded from features).
- **FR7.2**: An `IsolationForest` model shall be trained strictly on a time-based split of historical transactions. Contamination threshold tuning shall occur strictly within the training set.
- **FR7.3**: The ML model shall generate anomaly scores and flag suspicious transactions on the subsequent unseen test split.

#### FR8: Benchmark & Evaluation
- **FR8.1**: The system shall evaluate and benchmark Rules-Only, ML-Only (Isolation Forest), and Combined (Rules OR ML) approaches on the test dataset against synthetic ground-truth labels.
- **FR8.2**: Metrics calculated and reported shall include Precision, Recall, F1 Score, False Positive Rate (FPR), and daily alert workload volume.

---

### 3. Non-Functional Requirements (NFR)

#### NFR1: Performance & Scalability
- **NFR1.1**: The database schema and ingestion pipeline must comfortably handle over 100,000 transactions.
- **NFR1.2**: Bulk ingestion and rule replay must execute with batch sizes between 500 and 2,000 to maintain optimal memory and transaction commit overhead.

#### NFR2: Security & Passwords
- **NFR2.1**: User credentials in the `users` table must be hashed using BCrypt. Plaintext passwords must never be stored or logged.
- **NFR2.2**: Method-level security annotations (`@PreAuthorize`) must safeguard sensitive operations.

#### NFR3: Consistency & Atomicity
- **NFR3.1**: Alert status changes and corresponding audit entries must execute within a single `@Transactional` boundary. If the audit log fails to persist, the status transition must roll back completely.

#### NFR4: Testability & Quality Assurance
- **NFR4.1**: Backend Java code must achieve at least 80% line test coverage as measured and enforced by JaCoCo (`mvn verify`).
- **NFR4.2**: Python ML and data generation code must achieve at least 80% test coverage as measured and enforced by pytest-cov (`pytest --cov=ml --cov-fail-under=80`).

#### NFR5: Portability & CI/CD
- **NFR5.1**: The complete application stack (Backend and PostgreSQL database) must be orchestratable via `docker compose up`.
- **NFR5.2**: Continuous integration pipelines (GitHub Actions) must validate build compilation, tests, and coverage enforcement on every push and pull request.
