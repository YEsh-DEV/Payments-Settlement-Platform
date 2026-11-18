-- V7__journal_immutability_trigger.sql: Enforce append-only immutability on journals
CREATE OR REPLACE FUNCTION trg_prevent_journal_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Journals are strictly immutable and cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_journal_immutability ON journals;

CREATE TRIGGER trg_journal_immutability
BEFORE UPDATE OR DELETE ON journals
FOR EACH ROW EXECUTE FUNCTION trg_prevent_journal_modification();
