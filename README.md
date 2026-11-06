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
| **High Test Coverage** | Java 80%+ line coverage via JaCoCo; Python 80%+ coverage via `pytest-cov` |
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

# 3. Backend
cd backend
mvn clean spring-boot:run

# 4. Load Data into PostgreSQL
python ml/load_data.py
```

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
