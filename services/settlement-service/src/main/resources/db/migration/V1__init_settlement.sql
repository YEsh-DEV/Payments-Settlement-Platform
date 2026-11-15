-- V1__init_settlement.sql: Schema for PayCore Settlement & Reconciliation Service

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Batches Table (Cutoff Batches)
CREATE TABLE batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id VARCHAR(64) NOT NULL UNIQUE,
    cutoff_window VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    total_count INT NOT NULL DEFAULT 0,
    total_gross_paise BIGINT NOT NULL DEFAULT 0,
    total_fee_paise BIGINT NOT NULL DEFAULT 0,
    total_net_paise BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ
);

CREATE INDEX idx_batches_status ON batches (status);

-- Settlement Records (Candidates from Ledger Domain Events)
CREATE TABLE settlement_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id VARCHAR(64) NOT NULL UNIQUE,
    batch_id VARCHAR(64) REFERENCES batches(batch_id),
    payment_id VARCHAR(64) NOT NULL,
    seller_id VARCHAR(64) NOT NULL,
    provider_txn_id VARCHAR(128),
    order_id VARCHAR(64),
    kind VARCHAR(32) NOT NULL DEFAULT 'CAPTURE',
    gross_paise BIGINT NOT NULL,
    fee_paise BIGINT NOT NULL,
    net_paise BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_settlement_records_batch_id ON settlement_records (batch_id);
CREATE INDEX idx_settlement_records_status ON settlement_records (status);
CREATE INDEX idx_settlement_records_payment_id ON settlement_records (payment_id);
CREATE INDEX idx_settlement_records_provider_txn_id ON settlement_records (provider_txn_id);

-- Payouts generated per batch for sellers
CREATE TABLE settlement_payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_id VARCHAR(64) NOT NULL UNIQUE,
    batch_id VARCHAR(64) NOT NULL REFERENCES batches(batch_id),
    seller_id VARCHAR(64) NOT NULL,
    amount_paise BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    ledger_payout_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_settlement_payouts_batch ON settlement_payouts (batch_id);
CREATE INDEX idx_settlement_payouts_seller ON settlement_payouts (seller_id);

-- Provider Settlement Files (Ingested Bank/Provider Reports)
CREATE TABLE provider_settlement_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename VARCHAR(128) NOT NULL UNIQUE,
    file_hash VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(64) NOT NULL,
    total_records INT NOT NULL,
    total_amount_paise BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'INGESTED',
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Provider Lines (Streaming line records from provider files)
CREATE TABLE provider_lines (
    id BIGSERIAL PRIMARY KEY,
    file_id UUID NOT NULL REFERENCES provider_settlement_files(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    provider_batch_id VARCHAR(64),
    provider_txn_id VARCHAR(128) NOT NULL,
    txn_type VARCHAR(32) NOT NULL DEFAULT 'CAPTURE',
    merchant_ref VARCHAR(128),
    internal_reference VARCHAR(128),
    amount_paise BIGINT NOT NULL,
    fee_paise BIGINT NOT NULL DEFAULT 0,
    net_paise BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    txn_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    settle_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'INGESTED',
    raw_data TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provider_lines_file ON provider_lines (file_id);
CREATE INDEX idx_provider_lines_ref ON provider_lines (internal_reference);
CREATE INDEX idx_provider_lines_provider_txn ON provider_lines (provider_txn_id);
CREATE INDEX idx_provider_lines_merchant_ref ON provider_lines (merchant_ref);

-- Bank Deposit Records (from Bank Statement Files)
CREATE TABLE bank_deposits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_deposit_id VARCHAR(64) NOT NULL UNIQUE,
    value_date DATE NOT NULL,
    amount_paise BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    reference TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'UNMATCHED',
    matched_batch_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bank_deposits_status ON bank_deposits (status);
CREATE INDEX idx_bank_deposits_value_date ON bank_deposits (value_date);

-- Ground Truth Simulation Manifests (Generated in sim profile to verify accuracy)
CREATE TABLE simulation_manifests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id VARCHAR(64) NOT NULL,
    filename VARCHAR(128) NOT NULL,
    fault_type VARCHAR(64) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_simulation_manifests_batch ON simulation_manifests (batch_id);

-- Reconciliation Runs (Execution of the 4-Stage Reconciliation Engine)
CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id VARCHAR(64) NOT NULL UNIQUE,
    batch_id VARCHAR(64),
    file_id UUID,
    from_date DATE,
    to_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    matched_count INT NOT NULL DEFAULT 0,
    discrepancy_count INT NOT NULL DEFAULT 0,
    unmatched_count INT NOT NULL DEFAULT 0,
    total_variance_paise BIGINT NOT NULL DEFAULT 0,
    precision_pct DOUBLE PRECISION DEFAULT 0.0,
    recall_pct DOUBLE PRECISION DEFAULT 0.0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_recon_runs_status ON reconciliation_runs (status);

-- Reconciliation Matches (Line-level audit trail across stages)
CREATE TABLE reconciliation_matches (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL REFERENCES reconciliation_runs(run_id),
    record_id VARCHAR(64) NOT NULL,
    provider_line_id BIGINT REFERENCES provider_lines(id),
    bank_deposit_id VARCHAR(64),
    stage VARCHAR(32) NOT NULL,
    rule VARCHAR(64) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    evidence_json TEXT,
    matched_amount_paise BIGINT NOT NULL,
    variance_paise BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recon_matches_run ON reconciliation_matches (run_id);
CREATE INDEX idx_recon_matches_record ON reconciliation_matches (record_id);

-- Cases Table (Discrepancy & Exception Case Management)
CREATE TABLE cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id VARCHAR(64) NOT NULL UNIQUE,
    run_id VARCHAR(64) REFERENCES reconciliation_runs(run_id),
    record_id VARCHAR(64),
    provider_line_id BIGINT REFERENCES provider_lines(id),
    deposit_id VARCHAR(64),
    type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(64),
    proposed_action VARCHAR(64),
    proposed_by VARCHAR(64),
    approved_by VARCHAR(64),
    variance_paise BIGINT NOT NULL DEFAULT 0,
    sla_deadline TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT ck_cases_maker_checker CHECK (
        approved_by IS NULL OR proposed_by IS NULL OR approved_by <> proposed_by
    )
);

CREATE INDEX idx_cases_status ON cases (status);
CREATE INDEX idx_cases_sla_deadline ON cases (sla_deadline) WHERE status NOT IN ('RESOLVED', 'WRITTEN_OFF');

-- Case Notes (Timeline comments)
CREATE TABLE case_notes (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL REFERENCES cases(case_id) ON DELETE CASCADE,
    author VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Adjustments Table (Maker-checker adjustments)
CREATE TABLE adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    adjustment_id VARCHAR(64) NOT NULL UNIQUE,
    case_id VARCHAR(64) NOT NULL REFERENCES cases(case_id),
    template VARCHAR(32) NOT NULL,
    debit_account VARCHAR(64) NOT NULL,
    credit_account VARCHAR(64) NOT NULL,
    amount_paise BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
    proposed_by VARCHAR(64) NOT NULL,
    approved_by VARCHAR(64),
    ledger_journal_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_adjustments_case ON adjustments (case_id);
CREATE INDEX idx_adjustments_status ON adjustments (status);

-- Processed Events (Kafka Deduplication)
CREATE TABLE processed_events (
    event_id VARCHAR(128) PRIMARY KEY,
    source VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
