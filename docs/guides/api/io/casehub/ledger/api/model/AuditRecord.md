# io.casehub.ledger.api.model.AuditRecord

**Package:** `io.casehub.ledger.api.model`

**Kind:** `record`

Input value type for the `io.casehub.ledger.api.spi.LedgerAppender` write path.

<p>Captures the minimal information needed to append a ledger entry: who did what,
on which aggregate, and optionally when and why. The appender creates a concrete
`LedgerEntry` subclass from this record and delegates to the save pipeline.

<p>ATTESTATION entries are not supported — use `OutcomeRecord` with
`io.casehub.ledger.api.spi.OutcomeRecorder` for combined entry + attestation writes.

<p>Immutable — all with-methods return new instances.

## Fields

### `actorId` (`java.lang.String`)

### `actorRole` (`java.lang.String`)

### `actorType` (`ActorType`)

### `causedByEntryId` (`java.util.UUID`)

### `entryType` (`io.casehub.ledger.api.model.LedgerEntryType`)

### `metadata` (`java.lang.String`)

### `occurredAt` (`java.time.Instant`)

### `subjectId` (`java.util.UUID`)

## Record Components

### `actorId` (`java.lang.String`)

### `actorRole` (`java.lang.String`)

### `actorType` (`ActorType`)

### `causedByEntryId` (`java.util.UUID`)

### `entryType` (`io.casehub.ledger.api.model.LedgerEntryType`)

### `metadata` (`java.lang.String`)

### `occurredAt` (`java.time.Instant`)

### `subjectId` (`java.util.UUID`)

## Constructors

### `public AuditRecord(java.util.UUID subjectId, java.lang.String actorId, ActorType actorType, java.lang.String actorRole, io.casehub.ledger.api.model.LedgerEntryType entryType, java.time.Instant occurredAt, java.util.UUID causedByEntryId, java.lang.String metadata)`

#### Parameters

- `subjectId` (`java.util.UUID`)
- `actorId` (`java.lang.String`)
- `actorType` (`ActorType`)
- `actorRole` (`java.lang.String`)
- `entryType` (`io.casehub.ledger.api.model.LedgerEntryType`)
- `occurredAt` (`java.time.Instant`)
- `causedByEntryId` (`java.util.UUID`)
- `metadata` (`java.lang.String`)

## Methods

### `public java.lang.String actorId()`

### `public java.lang.String actorRole()`

### `public ActorType actorType()`

### `public java.util.UUID causedByEntryId()`

### `public io.casehub.ledger.api.model.LedgerEntryType entryType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public static io.casehub.ledger.api.model.AuditRecord event(java.lang.String actorId, java.util.UUID subjectId)`

Factory for a minimal EVENT record with AGENT actor type.

#### Parameters

- `actorId` (`java.lang.String`) — the actor identity
- `subjectId` (`java.util.UUID`) — the aggregate this entry belongs to

#### Returns

a new AuditRecord with EVENT type and AGENT actor type

### `public final int hashCode()`

### `public java.lang.String metadata()`

### `public java.time.Instant occurredAt()`

### `public java.util.UUID subjectId()`

### `public final java.lang.String toString()`

### `public io.casehub.ledger.api.model.AuditRecord withActorRole(java.lang.String role)`

#### Parameters

- `role` (`java.lang.String`)

#### Throws

- `NullPointerException` — if role is null

### `public io.casehub.ledger.api.model.AuditRecord withCausedBy(java.util.UUID entryId)`

#### Parameters

- `entryId` (`java.util.UUID`)

#### Throws

- `NullPointerException` — if entryId is null

### `public io.casehub.ledger.api.model.AuditRecord withMetadata(java.lang.String m)`

Attach consumer-provided freeform JSON context.

<p>Must be valid JSON. Must NOT contain personally identifiable information (PII) —
the GDPR Art.17 erasure mechanism does not scan field contents.

#### Parameters

- `m` (`java.lang.String`)

#### Throws

- `NullPointerException` — if m is null

### `public io.casehub.ledger.api.model.AuditRecord withOccurredAt(java.time.Instant ts)`

#### Parameters

- `ts` (`java.time.Instant`)

#### Throws

- `NullPointerException` — if ts is null
