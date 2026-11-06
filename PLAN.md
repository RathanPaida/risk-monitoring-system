# Transaction Risk Monitoring & Alerting System: Build Plan

Repo name: `risk-monitoring-system`
Goal: a small banking-style platform that ingests transactions, flags suspicious activity with a rule engine and an ML model, and routes alerts through a role-based review and escalation workflow with an immutable audit trail.

> **How to use this file:** give it to your coding assistant (Claude Code, Cursor, etc.) one phase at a time: "Implement Phase 2 of PLAN.md". Tell it to stop after each phase so you can run the tests and commit before moving on.

---

## 0. Rules for the AI coding agent

- Use only **synthetic data**. Never real customer or card data.
- Build exactly what this plan lists. Do not add extra services or frameworks.
- Every phase ends with: code compiles, tests pass, README updated, one git commit on a feature branch.
- Keep names consistent with this document (tables, endpoints, packages).
- Prefer simple, readable code over clever code. The owner must be able to explain every file in an interview.
- Write tests alongside code, not at the end.

## 1. Resume claims this project must satisfy

Every item below is on the resume, so each must be true and demonstrable when finished.

| Claim | How it is satisfied | Checked in |
|---|---|---|
| Spring Boot REST service with PostgreSQL | Phase 2-3 | `GET /actuator/health`, Swagger UI |
| Ingests 100K+ synthetic transactions | Phase 1 generator and bulk ingest | `SELECT count(*) FROM transactions;` ≥ 100000 |
| 4 fraud rules: velocity, unusual amount, geo-mismatch, new device | Phase 3 | 4 rule classes, 4 test classes |
| Review and escalation workflow | Phase 4 | Alert state machine and endpoints |
| Role-based access (analyst vs. manager) | Phase 4 | Security tests per role |
| Immutable audit log of every alert action | Phase 4 | DB trigger blocks UPDATE/DELETE, test proves it |
| Isolation Forest compared with rules on precision, recall, FPR | Phase 5 | `ml/reports/evaluation.md` |
| JUnit/pytest at 80%+ coverage | Phase 6 | JaCoCo and `--cov-fail-under=80` in CI |
| GitHub Actions CI and Docker | Phase 6 | Green CI badge, `docker compose up` works |
| SDLC workflow (requirements, user stories, PRs) | Phase 0 and 7 | `docs/` folder and merged PRs |

If a feature gets cut, remove or edit the matching resume bullet.

## 2. Tech stack

- **Backend:** Java 17, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, Spring Security, Spring Validation, Flyway, springdoc-openapi (Swagger UI)
- **Database:** PostgreSQL 16
- **ML and data:** Python 3.11, pandas, numpy, Faker, scikit-learn, psycopg2, matplotlib, pytest, pytest-cov
- **Testing:** JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL), JaCoCo
- **DevOps:** Docker, Docker Compose, GitHub Actions

## 3. Repository structure

```
risk-monitoring-system/
├── README.md
├── PLAN.md
├── docker-compose.yml
├── .github/
│   ├── workflows/ci.yml
│   └── pull_request_template.md
├── docs/
│   ├── requirements.md
│   ├── user-stories.md
│   ├── architecture.md          # contains the mermaid diagram
│   ├── api.md
│   └── sprint-board.md          # screenshot or link to GitHub Project board
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/rathan/riskmonitor/
│       │   ├── RiskMonitorApplication.java
│       │   ├── config/          # SecurityConfig, OpenApiConfig
│       │   ├── transaction/     # entity, repository, service, controller, dto
│       │   ├── rules/           # Rule interface, 4 rules, RuleEngine
│       │   ├── alert/           # entity, state machine, service, controller
│       │   ├── audit/           # AuditLog entity, AuditService, controller
│       │   ├── user/            # User entity, roles, UserDetailsService
│       │   └── common/          # exceptions, error handler
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/    # V1__schema.sql, V2__audit_immutable.sql, V3__seed_users.sql
│       └── test/java/...        # mirrors main
├── ml/
│   ├── requirements.txt
│   ├── generate_data.py         # synthetic data and fraud injection
│   ├── load_data.py             # bulk load into PostgreSQL
│   ├── features.py
│   ├── train_isolation_forest.py
│   ├── evaluate.py              # rules vs. ML comparison
│   ├── reports/                 # evaluation.md and plots
│   └── tests/                   # pytest
└── data/                        # generated CSVs, git-ignored
```

## 4. Database schema (Flyway `V1__schema.sql`)

```
customers(id PK, name, home_country, created_at)
accounts(id PK, customer_id FK, account_type, opened_at, status)
merchants(id PK, name, category, country)
devices(id PK, customer_id FK, device_hash, first_seen_at)

transactions(
  id PK, account_id FK, merchant_id FK, device_id FK,
  amount NUMERIC(12,2), currency, country,
  txn_time TIMESTAMPTZ, channel,          -- ONLINE, POS, ATM
  created_at
)
-- indexes: (account_id, txn_time), (device_id), (txn_time)

synthetic_labels(txn_id PK FK, is_fraud BOOLEAN, fraud_type)
-- ground truth for evaluation only; the backend never reads this table

alerts(
  id PK, txn_id FK, rule_name, severity,   -- LOW, MEDIUM, HIGH
  status,                                  -- OPEN, IN_REVIEW, ESCALATED, CLOSED_FALSE_POSITIVE, CLOSED_CONFIRMED
  assigned_to FK users NULL, reason TEXT,
  created_at, updated_at
)

users(id PK, username UNIQUE, password_hash, role)   -- ANALYST, MANAGER

audit_log(
  id BIGSERIAL PK, alert_id FK, action,    -- CREATED, CLAIMED, COMMENTED, ESCALATED, CLOSED
  actor, old_status, new_status, details,
  created_at DEFAULT now()
)
```

`V2__audit_immutable.sql`: create a trigger that raises an exception on `UPDATE` or `DELETE` of `audit_log`. Add a test that tries both and asserts failure.

Seed in `V3`: users `analyst1` (ANALYST) and `manager1` (MANAGER) with BCrypt-hashed demo passwords documented in the README.

## 5. Fraud rules (Phase 3)

Each rule implements `Rule { Optional<RuleHit> evaluate(Transaction t, RuleContext ctx); }`. Thresholds are in `application.yml`, not hard-coded.

| Rule | Logic | Default | Severity |
|---|---|---|---|
| **VelocityRule** | More than N transactions on the same account within a window | N=5 in 10 min | MEDIUM, HIGH if N>10 |
| **UnusualAmountRule** | Amount > K × the account's trailing 30-day average, and above a minimum | K=5, minimum 500 | MEDIUM, HIGH if K>10 |
| **GeoMismatchRule** | Transaction country differs from customer home country, or from the previous transaction's country within 1 hour | n/a | MEDIUM |
| **NewDeviceRule** | Device first seen on this account and amount above a threshold | threshold 200 | LOW, MEDIUM if amount>1000 |

`RuleEngine` runs all four on each transaction, creates one alert per rule hit, and writes a `CREATED` audit entry per alert.

## 6. Alert workflow and roles (Phase 4)

State machine:

```
OPEN → IN_REVIEW → ESCALATED → CLOSED_CONFIRMED | CLOSED_FALSE_POSITIVE
              └──→ CLOSED_CONFIRMED | CLOSED_FALSE_POSITIVE
```

Illegal transitions return HTTP 409.

| Action | ANALYST | MANAGER |
|---|---|---|
| View alerts and transactions | yes | yes |
| Claim alert (OPEN → IN_REVIEW) | yes | yes |
| Close alert from IN_REVIEW | yes | yes |
| Escalate alert (IN_REVIEW → ESCALATED) | yes | yes |
| Close an ESCALATED alert | no | yes |
| View audit log | no | yes |
| View all analysts' alerts | own only | all |

Every state change writes an audit row in the same database transaction as the change. If the audit write fails, the change rolls back.

## 7. REST API (document in Swagger and `docs/api.md`)

```
POST   /api/transactions                 ingest one transaction, run rules
POST   /api/transactions/bulk            ingest a batch (used by the loader)
GET    /api/transactions/{id}
GET    /api/alerts?status=&severity=&page=&size=
GET    /api/alerts/{id}
POST   /api/alerts/{id}/claim
POST   /api/alerts/{id}/escalate         body: { "reason": "..." }
POST   /api/alerts/{id}/close            body: { "outcome": "CONFIRMED|FALSE_POSITIVE", "comment": "..." }
GET    /api/audit?alertId=               MANAGER only
GET    /actuator/health
```

Auth: HTTP Basic backed by the `users` table, with method-level `@PreAuthorize`. Return a consistent JSON error shape for 400, 401, 403, 404 and 409.

## 8. Phases

### Phase 0: Setup and SDLC docs (about 1.5 hours)
- Create the repo, `.gitignore`, `README` skeleton and the folder structure above.
- Write `docs/requirements.md` (functional and non-functional) and `docs/user-stories.md` (about 10 stories, e.g. "As an analyst, I can claim an open alert so that no one else works on it", each with acceptance criteria).
- Write `docs/architecture.md` with a mermaid diagram: loader → API → rule engine → PostgreSQL ← ML scripts, plus the analyst and manager clients.
- Create a GitHub Project board with columns Backlog / In Progress / Review / Done and add the stories as issues.
- Add `.github/pull_request_template.md` (what changed, how tested, checklist).
- **Working rule from here on:** one feature branch per phase, merge through a pull request.

### Phase 1: Synthetic data (about 2 hours)
- `ml/generate_data.py` using Faker with a fixed random seed for reproducibility:
  - about 2,000 customers, 1-2 accounts each, 500 merchants in about 10 categories, 1-3 devices per customer
  - **100,000+ transactions** over about 90 days with realistic daily patterns (amounts log-normal, mostly home country, usual device)
  - inject about 1-2% fraud with labelled types: `velocity_burst`, `amount_spike`, `foreign_country`, `new_device_large`, and some `mixed`. Make some fraud deliberately subtle so rules miss it, and some normal behaviour deliberately odd (travel, large purchases) so rules false-alarm. This is what makes the rules-vs-ML comparison interesting and honest.
  - write `customers.csv`, `accounts.csv`, `merchants.csv`, `devices.csv`, `transactions.csv`, `labels.csv` to `data/`
- Add a pytest for the generator (row counts, seed reproducibility, fraud rate within range, no nulls in required columns).
- **Done when:** the CSVs exist and the tests pass.

### Phase 2: Backend skeleton and persistence (about 3 hours)
- Spring Boot project, Flyway migrations V1-V3, JPA entities and repositories.
- `POST /api/transactions` and `GET /api/transactions/{id}` with validation and error handling.
- `docker-compose.yml` with `postgres` and `backend` services; app reads DB settings from environment variables.
- `ml/load_data.py` loads the reference tables, labels and transactions into PostgreSQL. Insert in batches. Document the exact command in the README.
- **Done when:** `docker compose up` starts both services, Swagger loads, the loader fills the database, and `SELECT count(*) FROM transactions` is at least 100000.

### Phase 3: Rule engine (about 3 hours)
- Implement the four rules and `RuleEngine` (see section 5), wired to run on ingest.
- Efficient queries only: use the `(account_id, txn_time)` index for velocity and trailing-average lookups. Avoid loading whole tables into memory.
- Bulk path: replay all loaded transactions in time order through the engine (a `POST /api/admin/replay` endpoint or a CLI runner), so alerts exist for the full dataset. Measure and note the runtime in the README.
- Unit tests for every rule: a hit case, a clear miss, and a boundary case at the threshold.
- **Done when:** alerts exist in the DB for the full dataset and rule tests pass.

### Phase 4: Workflow, security and audit (about 3 hours)
- Users, roles and Spring Security as in sections 6-7.
- Alert endpoints and the state machine, with transactional audit writes.
- `V2` immutability trigger, with an integration test (Testcontainers) proving UPDATE and DELETE on `audit_log` fail.
- Security tests: analyst gets 403 on `/api/audit` and on closing ESCALATED alerts, manager succeeds, unauthenticated gets 401, illegal transition gets 409.
- **Done when:** a full scenario works end to end in Swagger: ingest → alert → claim → escalate → manager closes → audit log shows the whole trail.

### Phase 5: Isolation Forest and comparison (about 3 hours)
- `features.py`: per-transaction features computed from history, such as amount, amount ÷ account 30-day mean, transactions in the last 10 minutes, hours since previous transaction, country-differs-from-home flag, new-device flag, hour of day, channel (one-hot). No label leakage: never use `is_fraud` as a feature.
- `train_isolation_forest.py`: fit on a **time-based train split** (earlier data); score the later test split; choose the decision threshold on the training period (e.g. the top contamination % by anomaly score), not on the test set. Save the model with joblib.
- `evaluate.py`: on the **same test period**, read rule alerts from PostgreSQL (a transaction is "flagged by rules" if it has any alert) and the ML flags; join with `labels`; report for **Rules only**, **Isolation Forest only**, and **Combined (rules OR ML)**: precision, recall, F1, false-positive rate, plus the count of alerts per day (analyst workload).
- Write the results to `ml/reports/evaluation.md` with 1-2 plots and a short honest discussion: where the rules win, where ML wins, what each misses, and the trade-offs. Report whatever the numbers are, even if the ML model does not beat the rules.
- pytest the feature code and the metric calculations (tiny hand-made examples).
- **Done when:** `evaluation.md` has real numbers. Only then add any "reduced false positives by X%" claim to the resume, and only if it is true.

### Phase 6: Quality, Docker and CI (about 2 hours)
- JaCoCo with a **line coverage rule of 0.80** enforced in `mvn verify`. Add tests until it passes (service and rule classes first; exclude config and DTO boilerplate only if justified).
- pytest with `--cov=ml --cov-fail-under=80`.
- `backend/Dockerfile` as a multi-stage build (Maven build → slim JRE image).
- `.github/workflows/ci.yml` with two jobs:
  1. **backend:** set up JDK 17, `mvn -B verify` (Testcontainers needs Docker, which GitHub's Ubuntu runners provide)
  2. **ml:** set up Python 3.11, `pip install -r ml/requirements.txt`, `pytest --cov --cov-fail-under=80`
- Optional third job: `docker build` of the backend image.
- Add the CI status badge and coverage figures to the README.
- **Done when:** CI is green on `main`.

### Phase 7: Documentation, demo and release (about 1.5 hours)
- README: overview, architecture diagram, quick start (`docker compose up`, load data, replay, Swagger URL, demo logins), results table from `evaluation.md`, design decisions and known limitations (synthetic data, rule thresholds are illustrative, single-node, Basic auth for simplicity).
- Record a 2-minute demo (screen recording): start the stack, ingest a suspicious transaction, show the alert, claim and escalate as analyst, close as manager, show the audit log, then show the evaluation table.
- Tag `v1.0.0` and write release notes.

## 9. Two-day schedule

**Day 1:** Phase 0 → 1 → 2 → 3 (docs, data, backend, rules).
**Day 2:** Phase 4 → 5 → 6 → 7 (workflow and audit, ML comparison, CI and coverage, docs and demo).

If you run short on time, protect these in order: Phases 1-4 (the core claims), then Phase 6 (tests and CI), then Phase 5, then the demo video. Cut anything cut from the resume as well.

## 10. Acceptance checklist (tick before applying or interviewing)

- [x] `docker compose up` brings up the API and database from a clean clone
- [x] 100,000+ transactions loaded and replayed through the rule engine
- [x] All 4 rules implemented, configurable, and unit tested
- [x] Alert workflow enforces valid transitions and roles
- [x] Audit log rows cannot be updated or deleted (test proves it)
- [x] `evaluation.md` contains measured precision, recall and FPR for rules, ML and combined
- [x] Java coverage ≥ 80% enforced in the build; Python coverage ≥ 80% enforced in CI
- [x] CI is green on `main`; README badge present
- [x] Merged pull requests exist for each phase, with the PR template filled in
- [x] `docs/` contains requirements, user stories, architecture diagram and API notes
- [x] Repo name and link match the resume: `github.com/RathanPaida/risk-monitoring-system`
- [x] Resume bullets match what actually exists (edit them if anything was cut)

## 11. Interview talking points to prepare

- **Why both rules and ML?** Rules are explainable, auditable and cheap; ML catches patterns no one wrote a rule for; compare the numbers in `evaluation.md`.
- **Why a time-based split?** It avoids leaking future information into training.
- **Why is the audit log immutable at the database level?** Application bugs and admins cannot silently rewrite history.
- **Trade-off of the threshold:** a lower threshold catches more fraud but increases analyst workload, so show the alerts-per-day figures.
- **What you would do next:** streaming ingestion (Kafka), a feedback loop from analyst outcomes into model retraining, proper JWT or OAuth, and a dashboard.
- **Pull requests:** if you worked alone, say the PRs were self-reviewed and that you used them to keep a clean history. Do not claim peer reviews that did not happen.
