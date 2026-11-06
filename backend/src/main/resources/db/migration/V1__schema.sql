-- V1__schema.sql

CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    home_country VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE accounts (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    account_type VARCHAR(50) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(50) NOT NULL
);
CREATE INDEX idx_accounts_customer_id ON accounts(customer_id);

CREATE TABLE merchants (
    id BIGINT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    country VARCHAR(10) NOT NULL
);

CREATE TABLE devices (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    device_hash VARCHAR(64) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_devices_customer_id ON devices(customer_id);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);

CREATE TABLE transactions (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id),
    merchant_id BIGINT NOT NULL REFERENCES merchants(id),
    device_id BIGINT NOT NULL REFERENCES devices(id),
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    country VARCHAR(10) NOT NULL,
    txn_time TIMESTAMPTZ NOT NULL,
    channel VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_account_time ON transactions(account_id, txn_time);
CREATE INDEX idx_transactions_device ON transactions(device_id);
CREATE INDEX idx_transactions_time ON transactions(txn_time);

CREATE TABLE synthetic_labels (
    txn_id BIGINT PRIMARY KEY REFERENCES transactions(id),
    is_fraud BOOLEAN NOT NULL,
    fraud_type VARCHAR(50)
);

CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    txn_id BIGINT NOT NULL REFERENCES transactions(id),
    rule_name VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(50) NOT NULL,
    assigned_to BIGINT REFERENCES users(id),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_alerts_txn_id ON alerts(txn_id);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_assigned_to ON alerts(assigned_to);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT NOT NULL REFERENCES alerts(id),
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_alert_id ON audit_log(alert_id);
