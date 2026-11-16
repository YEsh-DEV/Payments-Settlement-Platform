-- V4__journal_idempotency_scope_backstop.sql
-- Adds scope column to journals and establishes compound unique constraint (scope, idempotency_key)
-- ensuring that no key can ever produce more than one journal under any crash or retry scenario.

ALTER TABLE journals ADD COLUMN IF NOT EXISTS scope VARCHAR(64) NOT NULL DEFAULT 'global';

ALTER TABLE journals DROP CONSTRAINT IF EXISTS journals_idempotency_key_key;
ALTER TABLE journals ADD CONSTRAINT uk_journals_scope_idempotency_key UNIQUE (scope, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_journals_scope_idemp ON journals (scope, idempotency_key);
