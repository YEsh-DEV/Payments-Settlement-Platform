-- V6__outbox_claim_support.sql: Add claim timestamp for transactional outbox SKIP LOCKED relay

ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_outbox_events_unpublished ON outbox_events (published_at, claimed_at, id);
