# REST API Specification

Base URL: `http://localhost:8080` (or as configured in `application.yml`)  
Interactive Documentation: `http://localhost:8080/swagger-ui.html`  
OpenAPI Specification: `http://localhost:8080/v3/api-docs`

Authentication: **HTTP Basic Auth** (`Authorization: Basic <base64(username:password)>`)
- Demo Analyst: `analyst1` / `Analyst123!`
- Demo Manager: `manager1` / `Manager123!`

---

## 1. Transactions Endpoints

### 1.1 Ingest Single Transaction
- **Method / Path:** `POST /api/transactions`
- **Roles:** `ANALYST`, `MANAGER`
- **Request Headers:** `Content-Type: application/json`
- **Request Body:**
  ```json
  {
    "accountId": 101,
    "merchantId": 12,
    "deviceId": 55,
    "amount": 250.75,
    "currency": "USD",
    "country": "US",
    "channel": "ONLINE",
    "txnTime": "2026-10-15T14:30:00Z"
  }
  ```
- **Response `201 Created`:**
  ```json
  {
    "id": 1,
    "accountId": 101,
    "merchantId": 12,
    "deviceId": 55,
    "amount": 250.75,
    "currency": "USD",
    "country": "US",
    "channel": "ONLINE",
    "txnTime": "2026-10-15T14:30:00Z",
    "createdAt": "2026-10-15T14:30:01Z",
    "alertsGenerated": 0
  }
  ```
- **Errors:** `400 Bad Request` (validation failures).

---

### 1.2 Bulk Ingest Transactions
- **Method / Path:** `POST /api/transactions/bulk`
- **Roles:** `ANALYST`, `MANAGER`
- **Request Body:** Array of transaction objects.
- **Response `200 OK`:**
  ```json
  {
    "ingestedCount": 500,
    "alertsGenerated": 8
  }
  ```

---

### 1.3 Get Transaction by ID
- **Method / Path:** `GET /api/transactions/{id}`
- **Roles:** `ANALYST`, `MANAGER`
- **Response `200 OK`:** Full transaction object.
- **Errors:** `404 Not Found`.

---

## 2. Alerts & Workflow Endpoints

### 2.1 List / Filter Alerts
- **Method / Path:** `GET /api/alerts?status={OPEN|IN_REVIEW|ESCALATED|CLOSED_CONFIRMED|CLOSED_FALSE_POSITIVE}&severity={LOW|MEDIUM|HIGH}&page=0&size=20`
- **Roles:** `ANALYST` (sees own assigned + open unassigned alerts), `MANAGER` (sees all alerts).
- **Response `200 OK`:** Paginated list of alerts.

---

### 2.2 Get Alert Details
- **Method / Path:** `GET /api/alerts/{id}`
- **Roles:** `ANALYST`, `MANAGER`
- **Response `200 OK`:** Alert object including transaction summary and current assignment.

---

### 2.3 Claim Alert
- **Method / Path:** `POST /api/alerts/{id}/claim`
- **Roles:** `ANALYST`, `MANAGER`
- **State Transition:** `OPEN` $\rightarrow$ `IN_REVIEW` (assigns to caller).
- **Response `200 OK`:** Updated alert.
- **Errors:** `409 Conflict` if alert is not in `OPEN` status.

---

### 2.4 Escalate Alert
- **Method / Path:** `POST /api/alerts/{id}/escalate`
- **Roles:** `ANALYST`, `MANAGER`
- **State Transition:** `IN_REVIEW` $\rightarrow$ `ESCALATED`.
- **Request Body:**
  ```json
  {
    "reason": "Suspicious transaction frequency and high amount"
  }
  ```
- **Response `200 OK`:** Updated alert.
- **Errors:** `409 Conflict` if alert is not `IN_REVIEW`.

---

### 2.5 Close Alert
- **Method / Path:** `POST /api/alerts/{id}/close`
- **Roles:**
  - `ANALYST`: Allowed if alert is in `IN_REVIEW` status. Returns `403 Forbidden` if alert is `ESCALATED`.
  - `MANAGER`: Allowed for both `IN_REVIEW` and `ESCALATED` statuses.
- **Request Body:**
  ```json
  {
    "outcome": "CONFIRMED", // or "FALSE_POSITIVE"
    "comment": "Verified unauthorized card usage with customer."
  }
  ```
- **State Transition:** `IN_REVIEW` or `ESCALATED` $\rightarrow$ `CLOSED_CONFIRMED` or `CLOSED_FALSE_POSITIVE`.
- **Response `200 OK`:** Updated alert.
- **Errors:** `409 Conflict` if alert is already closed or not in a valid state.

---

## 3. Audit Log Endpoints

### 3.1 Query Audit Trail
- **Method / Path:** `GET /api/audit?alertId={alertId}`
- **Roles:** `MANAGER` only.
- **Response `200 OK`:**
  ```json
  [
    {
      "id": 1001,
      "alertId": 45,
      "action": "CREATED",
      "actor": "system",
      "oldStatus": null,
      "newStatus": "OPEN",
      "details": "Triggered by VelocityRule (count=6 in 10m)",
      "createdAt": "2026-10-15T14:30:02Z"
    },
    {
      "id": 1002,
      "alertId": 45,
      "action": "CLAIMED",
      "actor": "analyst1",
      "oldStatus": "OPEN",
      "newStatus": "IN_REVIEW",
      "details": "Claimed by analyst1",
      "createdAt": "2026-10-15T14:35:10Z"
    }
  ]
  ```
- **Errors:** `403 Forbidden` for analysts and non-managers.

---

## 4. Administrative / Operational Endpoints

### 4.1 Replay Rule Engine over Historical Data
- **Method / Path:** `POST /api/admin/replay`
- **Roles:** `MANAGER`
- **Response `200 OK`:**
  ```json
  {
    "totalTransactions": 105000,
    "alertsGenerated": 1820,
    "executionTimeMs": 14250
  }
  ```

### 4.2 Health & Metrics
- **Method / Path:** `GET /actuator/health`
- **Roles:** Public / Unauthenticated
- **Response `200 OK`:** `{"status": "UP"}`

---

## 5. Reports & Model Artifacts Endpoints

### 5.1 List Machine Learning Reports & Models
- **Method / Path:** `GET /api/reports`
- **Roles:** `MANAGER` only (`ROLE_MANAGER`).
- **Response `200 OK`:** List of model binaries and evaluation reports stored in AWS S3 (or local fallbacks).
  ```json
  [
    {
      "name": "isolation_forest.joblib",
      "key": "models/isolation_forest.joblib",
      "category": "MODEL",
      "sizeBytes": 938009,
      "lastModified": "2026-11-06T15:17:00Z",
      "storageType": "S3"
    },
    {
      "name": "evaluation_summary.json",
      "key": "reports/evaluation_summary.json",
      "category": "REPORT",
      "sizeBytes": 1420,
      "lastModified": "2026-11-06T15:21:00Z",
      "storageType": "S3"
    }
  ]
  ```
- **Errors:** `401 Unauthorized` for unauthenticated requests, `403 Forbidden` for analysts.
