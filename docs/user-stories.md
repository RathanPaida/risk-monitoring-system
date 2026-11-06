# User Stories & Acceptance Criteria

### US-01: Real-Time Transaction Ingestion
**As an** external payment gateway or upstream core banking system,  
**I want to** submit a transaction payload to `POST /api/transactions`,  
**So that** the transaction is validated, persisted, and immediately evaluated for fraud indicators.

**Acceptance Criteria:**
- Given a valid transaction payload with `accountId`, `merchantId`, `deviceId`, `amount`, `currency`, `country`, `txnTime`, and `channel`, when posted to `/api/transactions`, the system returns `201 Created` with the persisted transaction details.
- Given a payload missing required fields or having negative amounts, the system returns `400 Bad Request` with structured error messages.
- Upon ingestion, the transaction is immediately processed by the rule engine.

---

### US-02: Automated Multi-Rule Fraud Detection
**As a** risk operations system,  
**I want to** automatically evaluate incoming transactions against velocity, unusual amount, geo-mismatch, and new device rules,  
**So that** suspicious behavior is flagged before funds settle.

**Acceptance Criteria:**
- If an account exceeds 5 transactions in 10 minutes, a `MEDIUM` severity alert is generated (`VelocityRule`); if over 10, a `HIGH` severity alert is generated.
- If a transaction amount exceeds 5x the account's trailing 30-day average and is over \$500, an `UnusualAmountRule` alert is generated.
- If a transaction country differs from the customer home country or changes within 1 hour, a `GeoMismatchRule` alert is generated.
- If a device is first seen for the account and amount is over \$200, a `NewDeviceRule` alert is generated.
- Every generated alert starts in `OPEN` status and logs a `CREATED` audit entry.

---

### US-03: View Alert Queue
**As a** fraud analyst,  
**I want to** filter and paginate through open and assigned alerts by status and severity,  
**So that** I can triage high-risk alerts effectively.

**Acceptance Criteria:**
- Calling `GET /api/alerts?status=OPEN&severity=HIGH` returns paginated alerts.
- An analyst only sees alerts assigned to them or unassigned `OPEN` alerts.
- A manager sees all alerts across all analysts.

---

### US-04: Claim Open Alert
**As a** fraud analyst,  
**I want to** claim an unassigned `OPEN` alert,  
**So that** no other analyst works on it concurrently.

**Acceptance Criteria:**
- Calling `POST /api/alerts/{id}/claim` transitions the alert status from `OPEN` to `IN_REVIEW`.
- The `assigned_to` field is set to the authenticated analyst's user ID.
- An audit log entry with action `CLAIMED` is recorded.
- Attempting to claim an alert that is already `IN_REVIEW`, `ESCALATED`, or `CLOSED` returns HTTP `409 Conflict`.

---

### US-05: Escalate Alert for Manager Review
**As a** fraud analyst,  
**I want to** escalate a complex suspicious alert to senior management with a justified reason,  
**So that** higher-authority approval can be obtained.

**Acceptance Criteria:**
- Calling `POST /api/alerts/{id}/escalate` with body `{"reason": "High net-worth foreign wire transfer"}` moves the alert from `IN_REVIEW` to `ESCALATED`.
- The escalation reason and actor are recorded in the audit log.
- Attempting to escalate an alert not currently `IN_REVIEW` returns HTTP `409 Conflict`.

---

### US-06: Resolve Escalated Alert
**As a** risk manager,  
**I want to** review and resolve an escalated alert as confirmed fraud or false positive,  
**So that** the investigation is finalized and appropriate loss prevention actions are taken.

**Acceptance Criteria:**
- A user with role `MANAGER` calling `POST /api/alerts/{id}/close` with outcome `CONFIRMED` or `FALSE_POSITIVE` moves the alert from `ESCALATED` to `CLOSED_CONFIRMED` or `CLOSED_FALSE_POSITIVE`.
- An analyst attempting to close an `ESCALATED` alert receives HTTP `403 Forbidden`.
- A `CLOSED` audit entry is recorded with outcome and manager remarks.

---

### US-07: Close Standard Alert
**As a** fraud analyst,  
**I want to** close an alert in my review queue as a false positive or confirmed fraud,  
**So that** routine alerts can be cleared quickly without manager overhead.

**Acceptance Criteria:**
- Calling `POST /api/alerts/{id}/close` on an alert in `IN_REVIEW` status closes the alert.
- The status becomes `CLOSED_CONFIRMED` or `CLOSED_FALSE_POSITIVE`.
- Audit log is updated with action `CLOSED`.

---

### US-08: Inspect Immutable Audit Log
**As a** compliance manager or auditor,  
**I want to** view the complete audit trail for any given alert,  
**So that** I can verify regulatory compliance and check who made each decision.

**Acceptance Criteria:**
- A user with role `MANAGER` calling `GET /api/audit?alertId={id}` receives the complete chronological sequence of actions (`CREATED`, `CLAIMED`, `ESCALATED`, `CLOSED`).
- A user with role `ANALYST` calling `GET /api/audit` receives HTTP `403 Forbidden`.
- Any SQL `UPDATE` or `DELETE` executed against `audit_log` raises a database exception via trigger.

---

### US-09: Replay Historical Transactions
**As a** risk system administrator,  
**I want to** replay an ingested batch of 100K+ historical transactions through the rule engine,  
**So that** alerts are populated across the complete dataset for testing and evaluation.

**Acceptance Criteria:**
- Calling `POST /api/admin/replay` replays transactions in chronological order.
- The rule engine evaluates each transaction against historical state at transaction time.
- The replay returns total transactions processed, total alerts generated, and elapsed execution time.

---

### US-10: ML Anomaly Detection & Comparison
**As a** risk data scientist,  
**I want to** train an Isolation Forest model and benchmark its precision, recall, and false positive rate against deterministic rules,  
**So that** the organization can identify complementary fraud detection capabilities and optimize analyst workload.

**Acceptance Criteria:**
- Historical data is split into time-based train and test sets without label leakage.
- Features include velocity, amount ratio to 30-day mean, device novelty, and geographic flags.
- `ml/evaluate.py` generates a comprehensive comparison report in `ml/reports/evaluation.md` comparing Rules Only, Isolation Forest Only, and Combined detection metrics.
