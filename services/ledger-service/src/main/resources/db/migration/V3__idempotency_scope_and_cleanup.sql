-- V3__idempotency_scope_and_cleanup.sql: Add caller scope and cleanup index to idempotency_keys

ALTER TABLE idempotency_keys DROP CONSTRAINT idempotency_keys_pkey;
ALTER TABLE idempotency_keys ADD COLUMN scope VARCHAR(128) NOT NULL DEFAULT 'global';
ALTER TABLE idempotency_keys ADD PRIMARY KEY (scope, key);
CREATE INDEX idx_idempotency_cleanup ON idempotency_keys (status, created_at);
