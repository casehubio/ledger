# io.casehub.ledger.api.spi.OutcomeRecorder

**Package:** `io.casehub.ledger.api.spi`

**Kind:** `interface`

## Methods

### `public abstract void addAttestation(java.util.UUID entryId, io.casehub.ledger.api.model.AttestationVerdict verdict, double confidence, java.lang.String capabilityTag)`

Append an attestation to an existing decision entry.
The entry must exist; `subjectId` is inherited from the entry.
The attestor is resolved from the configured default.

#### Parameters

- `entryId` (`java.util.UUID`) — the LedgerEntry ID returned by `.record`
- `verdict` (`io.casehub.ledger.api.model.AttestationVerdict`) — ENDORSED / CHALLENGED / SOUND / FLAGGED
- `confidence` (`double`) — weight in (0.0, 1.0]
- `capabilityTag` (`java.lang.String`) — scopes the attestation to a capability

#### Throws

- `IllegalArgumentException` — if the entry does not exist
- `IllegalStateException` — if the default attestor is not configured

### `public abstract java.util.UUID record(io.casehub.ledger.api.model.OutcomeRecord record)`

Write an `OutcomeRecord` as both a `LedgerEntry` (EVENT) and a
`LedgerAttestation`. Both writes commit in the same transaction.
For JPA consumers, both are visible in the database before this method returns.

#### Parameters

- `record` (`io.casehub.ledger.api.model.OutcomeRecord`)

#### Returns

the UUID of the created `LedgerEntry`, for use with `.addAttestation`

#### Throws

- `IllegalStateException` — if `record.attestorId()` is null and
                              `casehub.ledger.outcome.default-attestor-id` is not configured
