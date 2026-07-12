-- V1011 — Add metadata column for consumer-provided audit context
ALTER TABLE ledger_entry ADD COLUMN metadata TEXT;
