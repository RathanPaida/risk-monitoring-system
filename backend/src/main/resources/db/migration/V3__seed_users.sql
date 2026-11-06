-- V3__seed_users.sql
-- Seed default demo users for ANALYST and MANAGER roles

INSERT INTO users (username, password_hash, role)
VALUES 
    ('analyst1', '$2a$10$jLfLR9q.maEPCWQSxiijPeQtmpaMUmnJ31toppj.YLW8z2C/NT5LO', 'ROLE_ANALYST'),
    ('manager1', '$2a$10$1HGeVMwYf5K.Z53AGmVyb.igGELhxZl3DmS1cJPIstvWWiFQU8VI6', 'ROLE_MANAGER')
ON CONFLICT (username) DO NOTHING;
