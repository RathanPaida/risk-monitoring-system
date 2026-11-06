# Transaction Risk Monitoring & Alerting System

[![CI Build](https://github.com/RathanPaida/risk-monitoring-system/actions/workflows/ci.yml/badge.svg)](https://github.com/RathanPaida/risk-monitoring-system/actions)
![Java](https://img.shields.io/badge/Java-17%2F25-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)
![Python](https://img.shields.io/badge/Python-3.10%2F3.11-blue.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%2F18-blue.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

A high-performance banking risk platform that ingests high-volume transactions (100K+ records), flags suspicious activity using both deterministic rules and an unsupervised machine learning model (Isolation Forest), and routes alerts through a role-based review and escalation workflow supported by an immutable audit trail.

---

## Key Capabilities & Resume Highlights

| Capability | Implementation & Proof Point |
|---|---|
| **Spring Boot REST Service & PostgreSQL** | Robust REST API, Flyway migrations (`V1`-`V3`), OpenAPI / Swagger UI at `/swagger-ui.html` |
| **High-Volume Ingestion** | Generates and loads **100,000+** realistic synthetic financial transactions over 90 days |
| **Deterministic Rule Engine** | 4 configurable fraud detection rules: Velocity, Unusual Amount, Geo-Mismatch, New Device |
| **Stateful Alert Workflow** | Finite state machine (`OPEN` $\rightarrow$ `IN_REVIEW` $\rightarrow$ `ESCALATED` $\rightarrow$ `CLOSED`) |
| **Role-Based Access Control (RBAC)** | Strict privilege separation between `ANALYST` and `MANAGER` via Spring Security |
| **Immutable Audit Log** | PostgreSQL trigger prevents `UPDATE` and `DELETE` on audit log; verified by automated tests |
| **Unsupervised ML Anomaly Detection** | Isolation Forest model trained on time-based split, benchmarked against rules on precision, recall, and FPR |
| **High Test Coverage** | Java 83.9% line coverage via JaCoCo; Python 93.1% coverage via `pytest-cov` (≥80% build & CI enforced) |
| **DevOps & CI/CD** | Multi-stage Docker build, Docker Compose, automated GitHub Actions CI pipeline |

---

## Tech Stack

- **Backend:** Java 17+, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, Spring Security, Flyway, Springdoc OpenAPI (Swagger UI)
- **Database:** PostgreSQL 16+
- **Machine Learning & Data:** Python 3.10+, pandas, numpy, scikit-learn, Faker, psycopg2, matplotlib, pytest, pytest-cov
- **Testing:** JUnit 5, Mockito, Spring Boot Test, JaCoCo, pytest
- **DevOps:** Docker, Docker Compose, GitHub Actions

---

## Architecture

See [docs/architecture.md](docs/architecture.md) for full architectural documentation and Mermaid diagrams.

---

## Quick Start

### 1. Prerequisites
- Java 17 or higher
- Maven 3.9+
- Python 3.10+
- PostgreSQL 16+ (or Docker)

### 2. Running with Docker Compose
```bash
docker compose up -d
```

### 3. Running Locally
```bash
# 1. Generate Synthetic Transactions (105,000 txns, 1.5% fraud)
python ml/generate_data.py

# 2. Run Python Generator Tests
pytest ml/tests/ -v

# 3. Backend (compiles on Java 17+, starts Flyway migrations V1-V3)
cd backend
mvn clean spring-boot:run

# 4. Load Data into PostgreSQL (loads 105,000 txns & reference tables)
python ml/load_data.py --port 5433

# 5. Replay Historical Transactions through Rule Engine
curl -u manager1:Manager123! -X POST http://localhost:8080/api/admin/replay
# Output: {"totalTransactions": 105000, "alertsGenerated": 4102, "executionTimeMs": 1233}

# 6. Verify PostgreSQL Transaction & Alert Counts
# SELECT count(*) FROM transactions;  --> 105,000
# SELECT count(*) FROM alerts;        --> 4,102
# SELECT count(*) FROM audit_log;     --> 4,102

# 7. Run Backend Integration & Security Tests
cd backend
mvn test
# Result: 31 tests passing (Rule tests, Security RBAC, State Machine, and Audit Log Immutability)

# 8. Train and Evaluate Machine Learning Model (Isolation Forest)
cd ..
python ml/train_isolation_forest.py
python ml/evaluate.py --port 5433
# Runs out-of-time evaluation benchmarking Rules vs ML, produces ml/reports/evaluation.md & PNG plots
```

---

## Machine Learning & Evaluation Benchmark

We benchmarked the **Deterministic Rule Engine (4 rules)** against an **Unsupervised Isolation Forest** model and an **Ensemble Hybrid (Rules OR ML)** on an out-of-time test split of **24,150 transactions** (20.7 days, 399 ground-truth frauds):

| Detection Strategy | Precision | Recall | F1 Score | False Positive Rate (FPR) | Total Alerts | Alerts / Day |
|---|---|---|---|---|---|---|
| **Rules Only** | 33.38% | 64.91% | 0.4409 | 2.18% | 776 | 37.4 |
| **Isolation Forest (ML)** | 27.05% | 40.60% | 0.3246 | 1.84% | 599 | 28.9 |
| **Combined (Rules OR ML)** | 24.59% | **67.42%** | 0.3603 | 3.47% | 1,094 | 52.7 |

### Visualizations & Detailed Report
- **Metrics Comparison Plot:** `ml/reports/metrics_comparison.png`
- **Analyst Workload Trade-off Plot:** `ml/reports/workload_tradeoff.png`
- **Full In-Depth Report:** See [ml/reports/evaluation.md](ml/reports/evaluation.md) for detailed discussion on where deterministic rules outperform ML (auditability, deterministic edge cases), where Isolation Forest wins (subtle multi-dimensional shifts), and real-world triage trade-offs.

---

### 4. Interactive Swagger UI
Access the Swagger UI at:
`http://localhost:8080/swagger-ui.html`

Demo Credentials:
- **Analyst:** `analyst1` / `Analyst123!`
- **Manager:** `manager1` / `Manager123!`

---

## SDLC Documentation
- [docs/requirements.md](docs/requirements.md): Functional and non-functional requirements
- [docs/user-stories.md](docs/user-stories.md): 10 user stories with acceptance criteria
- [docs/architecture.md](docs/architecture.md): Architecture diagram and component breakdown
- [docs/api.md](docs/api.md): REST API endpoints documentation
- [docs/sprint-board.md](docs/sprint-board.md): Sprint task tracking board
