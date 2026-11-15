-- V1__init_ledger.sql: Schema for PayCore Double-Entry Ledger Service

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Accounts Table
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(64) NOT NULL UNIQUE,
    type VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance_paise BIGINT NOT NULL DEFAULT 0,
    locked_balance_paise BIGINT NOT NULL DEFAULT 0,
    is_hot BOOLEAN NOT NULL DEFAULT FALSE,
    stripe_count INT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_accounts_currency_inr CHECK (currency = 'INR')
);

CREATE INDEX idx_accounts_code ON accounts (code);
CREATE INDEX idx_accounts_is_hot ON accounts (is_hot);

-- Account Stripes (For S2 STRIPED Hot-Account Strategy)
CREATE TABLE account_stripes (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    stripe_index INT NOT NULL,
    balance_paise BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, stripe_index)
);

-- Account Snapshots (For checkpointing balances & fast statement reconstruction)
CREATE TABLE account_snapshots (
    id BIGSERIAL PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    as_of_entry_id BIGINT NOT NULL,
    balance_paise BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_account_snapshots_lookup ON account_snapshots (account_id, as_of_entry_id DESC);

-- Sellers Table
CREATE TABLE sellers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    fee_tier VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    fee_bps INT NOT NULL DEFAULT 200,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_sellers_fee_bps CHECK (fee_bps >= 0 AND fee_bps <= 10000)
);

-- Journals Table
CREATE TABLE journals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    reference_id VARCHAR(128),
    type VARCHAR(32) NOT NULL,
    description TEXT,
    posted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_journals_reference ON journals (reference_id);
CREATE INDEX idx_journals_posted_at ON journals (posted_at);

-- Ledger Entries Table (Monotonic Sequence ID, Immutability via Trigger I2)
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    journal_id UUID NOT NULL REFERENCES journals(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    direction VARCHAR(2) NOT NULL CHECK (direction IN ('DR', 'CR')),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    running_balance_paise BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_entries_account_id_seq ON ledger_entries (account_id, id ASC);
CREATE INDEX idx_ledger_entries_journal_id ON ledger_entries (journal_id);

-- Invariant I2 Immutability Trigger
CREATE OR REPLACE FUNCTION trg_prevent_ledger_entry_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries are strictly immutable and cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entry_immutability
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION trg_prevent_ledger_entry_modification();

-- Payments Table
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id VARCHAR(64) NOT NULL UNIQUE,
    seller_id VARCHAR(64) NOT NULL REFERENCES sellers(seller_id),
    gross_paise BIGINT NOT NULL CHECK (gross_paise > 0),
    fee_paise BIGINT NOT NULL CHECK (fee_paise >= 0),
    net_paise BIGINT NOT NULL CHECK (net_paise >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(32) NOT NULL,
    journal_id UUID REFERENCES journals(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_seller ON payments (seller_id);

-- Refunds Table
CREATE TABLE refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_id VARCHAR(64) NOT NULL UNIQUE,
    payment_id VARCHAR(64) NOT NULL REFERENCES payments(payment_id),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    reason TEXT,
    status VARCHAR(32) NOT NULL,
    journal_id UUID REFERENCES journals(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refunds_payment ON refunds (payment_id);

-- Holds Table
CREATE TABLE holds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hold_id VARCHAR(64) NOT NULL UNIQUE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    reason TEXT,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_holds_account_active ON holds (account_id, status);
CREATE INDEX idx_holds_expires_at ON holds (expires_at) WHERE status = 'ACTIVE';

-- Payouts Table
CREATE TABLE payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_id VARCHAR(64) NOT NULL UNIQUE,
    seller_id VARCHAR(64) NOT NULL REFERENCES sellers(seller_id),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    status VARCHAR(32) NOT NULL,
    batch_id VARCHAR(64),
    journal_id UUID REFERENCES journals(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payouts_seller ON payouts (seller_id);
CREATE INDEX idx_payouts_batch ON payouts (batch_id);

-- Idempotency Keys Table (F-L-10)
CREATE TABLE idempotency_keys (
    key VARCHAR(128) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Outbox Events Table
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_unpublished ON outbox_events (id ASC) WHERE published_at IS NULL;

-- Processed Events (Kafka Deduplication)
CREATE TABLE processed_events (
    event_id VARCHAR(128) PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
