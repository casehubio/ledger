# io.casehub.ledger.api.spi.LedgerAppender

**Package:** `io.casehub.ledger.api.spi`

**Kind:** `interface`

Value-type write SPI for appending audit entries to the ledger.

<p>Accepts an `AuditRecord` — a plain value type with no JPA dependency —
and returns the UUID of the persisted ledger entry. The implementation creates
the appropriate `LedgerEntry` subclass and delegates to the save pipeline
(sequence assignment, hash chain, enrichment).

<p>This is the primary write path for api-tier consumers that need to record
events and commands without depending on runtime internals. For combined
entry + attestation writes, use `OutcomeRecorder` instead.

<p>ATTESTATION entries are not supported via this path — `AuditRecord`
rejects them at construction time.

## Methods

### `public abstract java.util.UUID append(io.casehub.ledger.api.model.AuditRecord record, java.lang.String tenancyId)`

Append a new audit entry to the ledger.

#### Parameters

- `record` (`io.casehub.ledger.api.model.AuditRecord`) — the audit record to persist; must not be `null`
- `tenancyId` (`java.lang.String`) — the tenant scope for this entry

#### Returns

the UUID of the persisted ledger entry
