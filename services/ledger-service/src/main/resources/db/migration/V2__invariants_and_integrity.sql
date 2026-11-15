-- V2__invariants_and_integrity.sql: Enforce Invariant I8 unique constraints on business operation journals

ALTER TABLE payments ADD CONSTRAINT uq_payments_journal_id UNIQUE (journal_id);
ALTER TABLE refunds ADD CONSTRAINT uq_refunds_journal_id UNIQUE (journal_id);
ALTER TABLE payouts ADD CONSTRAINT uq_payouts_journal_id UNIQUE (journal_id);
