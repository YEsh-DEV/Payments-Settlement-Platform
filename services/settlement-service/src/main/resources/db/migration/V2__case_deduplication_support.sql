-- V2__case_deduplication_support.sql
-- Add reference_id and unique index on (type, reference_id) to prevent duplicate cases on repeated reconciliation runs

ALTER TABLE cases ADD COLUMN IF NOT EXISTS reference_id VARCHAR(128);

UPDATE cases
SET reference_id = COALESCE(record_id, CAST(provider_line_id AS VARCHAR), deposit_id)
WHERE reference_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_cases_type_reference ON cases (type, reference_id) WHERE reference_id IS NOT NULL;
