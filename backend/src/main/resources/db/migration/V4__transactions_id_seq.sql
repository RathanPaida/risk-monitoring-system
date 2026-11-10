-- V4__transactions_id_seq.sql
-- Provide sequence default for transactions.id to align with JPA GenerationType.IDENTITY

CREATE SEQUENCE IF NOT EXISTS transactions_id_seq START WITH 200000;
ALTER TABLE transactions ALTER COLUMN id SET DEFAULT nextval('transactions_id_seq');

