# io.casehub.ledger.api.spi.LedgerEntryRepository

**Package:** `io.casehub.ledger.api.spi`

**Kind:** `interface`

SPI for persisting and querying `LedgerEntry` and `LedgerAttestation` records.

<p>
Queries operate on the base `LedgerEntry` type — polymorphic results include all
registered subclasses. The built-in implementation (`JpaLedgerEntryRepository`)
uses Hibernate ORM + EntityManager directly. Alternative implementations can be
substituted via CDI.

## Methods

### `public abstract java.util.List<io.casehub.ledger.api.model.LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(java.lang.String attestorId, java.lang.String capabilityTag, java.lang.String tenancyId)`

Return all attestations written by the given attestor for the given capability tag,
across all ledger entries, ordered by occurrence time ascending.

<p>Used by B2 trust scoring to aggregate per-actor, per-capability attestation history.

#### Parameters

- `attestorId` (`java.lang.String`) — the attestor identity
- `capabilityTag` (`java.lang.String`) — the capability tag to filter by; never `null`
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

ordered list; empty if none match

### `public abstract java.util.List<io.casehub.ledger.api.model.LedgerAttestation> findAttestationsByEntryId(java.util.UUID ledgerEntryId, java.lang.String tenancyId)`

Return all attestations for the given ledger entry, ordered by occurrence time ascending.

#### Parameters

- `ledgerEntryId` (`java.util.UUID`) — the ledger entry UUID
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

ordered list of attestations; empty if none exist

### `public abstract java.util.List<io.casehub.ledger.api.model.LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(java.util.UUID entryId, java.lang.String capabilityTag, java.lang.String tenancyId)`

Return all attestations for the given ledger entry scoped to the given capability tag,
ordered by occurrence time ascending. Use `io.casehub.ledger.api.model.CapabilityTag.GLOBAL`
to query global attestations explicitly.

#### Parameters

- `entryId` (`java.util.UUID`) — the ledger entry UUID
- `capabilityTag` (`java.lang.String`) — the capability tag to filter by; never `null`
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

ordered list; empty if none match

### `public abstract java.util.List<io.casehub.ledger.api.model.LedgerAttestation> findAttestationsByEntryIdGlobal(java.util.UUID entryId, java.lang.String tenancyId)`

Return all global attestations for the given ledger entry (where
`capabilityTag =``io.casehub.ledger.api.model.CapabilityTag.GLOBAL`),
ordered by occurrence time ascending.

#### Parameters

- `entryId` (`java.util.UUID`) — the ledger entry UUID
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

ordered list; empty if none exist

### `public abstract java.util.List<io.casehub.ledger.api.model.LedgerEntry> findByActorId(java.lang.String actorId, java.time.Instant from, java.time.Instant to, java.lang.String tenancyId)`

Return all ledger entries for the given actor whose `occurredAt` falls
within [`from`, `to`] inclusive, ordered by `occurredAt` ascending.

<p>
Provides the auditor-facing reconstructability required by EU AI Act Art.12 and
GDPR Art.22: "show everything actor X did between dates Y and Z."

<p>
Uses `Instant` rather than `LocalDateTime` — `occurredAt` is stored
as `Instant` throughout the codebase; `LocalDateTime` would require implicit
timezone conversion, creating a silent correctness hazard for distributed systems.

#### Parameters

- `actorId` (`java.lang.String`) — the actor identity to filter by
- `from` (`java.time.Instant`) — start of the time range (inclusive)
- `to` (`java.time.Instant`) — end of the time range (inclusive)
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

ordered list of entries; empty if none match

### `public abstract java.util.List<io.casehub.ledger.api.model.LedgerEntry> findByActorRole(java.lang.String actorRole, java.time.Instant from, java.time.Instant to, java.lang.String tenancyId)`

Return all ledger entries for the given actor role whose `occurredAt` falls
within [`from`, `to`] inclusive, ordered by `occurredAt` ascending.

#### Parameters

- `actorRole` (`java.lang.String`) — the functional role to filter by (e.g. `"Classifier"`)
- `from` (`java.time.Instant`) — start of the time range (inclusive)
- `to` (`java.time.Instant`) — end of the time range (inclusive)
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

ordered list of entries; empty if none match

### `public abstract java.util.List<io.casehub.ledger.api.model.LedgerEntry> findBySubjectId(java.util.UUID subjectId, java.lang.String tenancyId)`

Return all ledger entries for the given subject in sequence order (ascending).

#### Parameters

- `subjectId` (`java.util.UUID`) — the aggregate identifier
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

ordered list of entries; empty if none exist

### `public abstract java.util.List<io.casehub.ledger.api.model.LedgerEntry> findBySubjectIdAndTimeRange(java.util.UUID subjectId, java.time.Instant from, java.time.Instant to, java.lang.String tenancyId)`

Return all ledger entries for the given subject whose `occurredAt` falls
within [`from`, `to`] inclusive, ordered by `occurredAt` ascending.

#### Parameters

- `subjectId` (`java.util.UUID`) — the aggregate identifier
- `from` (`java.time.Instant`) — start of the time range (inclusive)
- `to` (`java.time.Instant`) — end of the time range (inclusive)
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

ordered list; empty if none match

### `public abstract java.util.List<io.casehub.ledger.api.model.LedgerEntry> findCausedBy(java.util.UUID entryId, java.lang.String tenancyId)`

Return all ledger entries causally triggered by the given entry,
ordered by `occurredAt` ascending. One hop only — recursive
traversal is the caller's responsibility.

#### Parameters

- `entryId` (`java.util.UUID`) — the entry whose direct effects to retrieve
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

ordered list; empty if none

### `public abstract java.util.Optional<io.casehub.ledger.api.model.LedgerEntry> findEntryById(java.util.UUID id, java.lang.String tenancyId)`

Return a ledger entry by its primary key.

#### Parameters

- `id` (`java.util.UUID`) — the ledger entry UUID primary key
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

the entry if found, or empty

### `public abstract java.util.Optional<io.casehub.ledger.api.model.LedgerEntry> findLatestBySubjectId(java.util.UUID subjectId, java.lang.String tenancyId)`

Return the most recent ledger entry for the given subject, or empty if none.

#### Parameters

- `subjectId` (`java.util.UUID`) — the aggregate identifier
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

the latest entry, or empty if no entries exist

### `public abstract io.casehub.ledger.api.model.LedgerEntry save(io.casehub.ledger.api.model.LedgerEntry entry, java.lang.String tenancyId)`

Persist a new ledger entry with automatic sequence number assignment.

<p>The repository assigns `sequenceNumber` based on the entry's
`subjectId` — any value set by the caller is overwritten. Sequence
numbers are monotonically increasing and contiguous on insert within
committed transactions. Retention deletion may remove entries from the
start of the sequence without affecting the contiguity invariant.

#### Parameters

- `entry` (`io.casehub.ledger.api.model.LedgerEntry`) — the entry to persist; `subjectId` must not be `null`
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

the persisted entry with `sequenceNumber` assigned

### `public abstract io.casehub.ledger.api.model.LedgerAttestation saveAttestation(io.casehub.ledger.api.model.LedgerAttestation attestation, java.lang.String tenancyId)`

Persist a new attestation and return the saved instance.

#### Parameters

- `attestation` (`io.casehub.ledger.api.model.LedgerAttestation`) — the attestation to persist; must not be `null`
- `tenancyId` (`java.lang.String`) — the tenant scope

#### Returns

the persisted attestation
