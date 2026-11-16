-- V3__maker_checker_check_constraint.sql
-- Defense-in-depth: Enforce dual-control separation of duties at the physical database layer.
-- An adjustment's approver must never be identical to the proposer.

ALTER TABLE adjustments
ADD CONSTRAINT chk_adjustments_maker_checker
CHECK (approved_by IS NULL OR approved_by <> proposed_by);
