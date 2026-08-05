# io.casehub.ledger.api.model.OutcomeRecord

**Package:** `io.casehub.ledger.api.model`

**Kind:** `record`

Captures a single plugin decision and its outcome for recording via `io.casehub.ledger.api.spi.OutcomeRecorder`.

<p>Use UUID, String, AttestationVerdict, double) for routing-aware writes.
Use UUID, AttestationVerdict, double) only when capability-differentiated
routing is not the goal — GLOBAL-scoped attestations do not reach `TrustScoreCache`
and therefore do not influence `TrustWeightedAgentStrategy`.

<p>Confidence in (0.0, 1.0]. Recommended values: 0.1 (tick-level), 0.7 (game-level), 1.0 (session).

## Fields

### `actorId` (`java.lang.String`)

### `actorRole` (`java.lang.String`)

### `actorType` (`ActorType`)

### `attestorId` (`java.lang.String`)

### `attestorType` (`ActorType`)

### `capabilityTag` (`java.lang.String`)

### `confidence` (`double`)

### `metadata` (`java.lang.String`)

### `occurredAt` (`java.time.Instant`)

### `subjectId` (`java.util.UUID`)

### `verdict` (`io.casehub.ledger.api.model.AttestationVerdict`)

## Record Components

### `actorId` (`java.lang.String`)

### `actorRole` (`java.lang.String`)

### `actorType` (`ActorType`)

### `attestorId` (`java.lang.String`)

### `attestorType` (`ActorType`)

### `capabilityTag` (`java.lang.String`)

### `confidence` (`double`)

### `metadata` (`java.lang.String`)

### `occurredAt` (`java.time.Instant`)

### `subjectId` (`java.util.UUID`)

### `verdict` (`io.casehub.ledger.api.model.AttestationVerdict`)

## Constructors

### `public OutcomeRecord(java.lang.String actorId, java.util.UUID subjectId, io.casehub.ledger.api.model.AttestationVerdict verdict, double confidence, java.lang.String capabilityTag, ActorType actorType, java.lang.String actorRole, java.time.Instant occurredAt, java.lang.String attestorId, ActorType attestorType, java.lang.String metadata)`

#### Parameters

- `actorId` (`java.lang.String`)
- `subjectId` (`java.util.UUID`)
- `verdict` (`io.casehub.ledger.api.model.AttestationVerdict`)
- `confidence` (`double`)
- `capabilityTag` (`java.lang.String`)
- `actorType` (`ActorType`)
- `actorRole` (`java.lang.String`)
- `occurredAt` (`java.time.Instant`)
- `attestorId` (`java.lang.String`)
- `attestorType` (`ActorType`)
- `metadata` (`java.lang.String`)

## Methods

### `public java.lang.String actorId()`

### `public java.lang.String actorRole()`

### `public ActorType actorType()`

### `public java.lang.String attestorId()`

### `public ActorType attestorType()`

### `public java.lang.String capabilityTag()`

### `public double confidence()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String metadata()`

### `public java.time.Instant occurredAt()`

### `public static io.casehub.ledger.api.model.OutcomeRecord of(java.lang.String actorId, java.util.UUID subjectId, java.lang.String capabilityTag, io.casehub.ledger.api.model.AttestationVerdict verdict, double confidence)`

Primary factory for routing-aware outcome recording.
capabilityTag is required — GLOBAL-scoped attestations do not reach TrustScoreCache.

#### Parameters

- `actorId` (`java.lang.String`)
- `subjectId` (`java.util.UUID`)
- `capabilityTag` (`java.lang.String`)
- `verdict` (`io.casehub.ledger.api.model.AttestationVerdict`)
- `confidence` (`double`)

### `public static io.casehub.ledger.api.model.OutcomeRecord ofGlobal(java.lang.String actorId, java.util.UUID subjectId, io.casehub.ledger.api.model.AttestationVerdict verdict, double confidence)`

Factory for outcomes that intentionally target the global Beta score only.
These do NOT reach TrustScoreCache or TrustWeightedAgentStrategy.

#### Parameters

- `actorId` (`java.lang.String`)
- `subjectId` (`java.util.UUID`)
- `verdict` (`io.casehub.ledger.api.model.AttestationVerdict`)
- `confidence` (`double`)

### `public java.util.UUID subjectId()`

### `public final java.lang.String toString()`

### `public io.casehub.ledger.api.model.AttestationVerdict verdict()`

### `public io.casehub.ledger.api.model.OutcomeRecord withActorRole(java.lang.String role)`

#### Parameters

- `role` (`java.lang.String`)

#### Throws

- `NullPointerException` — if role is null

### `public io.casehub.ledger.api.model.OutcomeRecord withActorType(ActorType t)`

#### Parameters

- `t` (`ActorType`)

#### Throws

- `NullPointerException` — if t is null.
Pass ActorType.AGENT explicitly to reset to the default rather than passing null.

### `public io.casehub.ledger.api.model.OutcomeRecord withAttestor(java.lang.String id, ActorType t)`

Override the attestor. Both id and type must be non-null —
they are always set together to maintain the pair invariant.

#### Parameters

- `id` (`java.lang.String`)
- `t` (`ActorType`)

### `public io.casehub.ledger.api.model.OutcomeRecord withMetadata(java.lang.String m)`

Attach consumer-provided freeform JSON context.

<p>Must be valid JSON. Must NOT contain personally identifiable information (PII) —
the GDPR Art.17 erasure mechanism does not scan field contents.

#### Parameters

- `m` (`java.lang.String`)

#### Throws

- `NullPointerException` — if m is null

### `public io.casehub.ledger.api.model.OutcomeRecord withOccurredAt(java.time.Instant ts)`

#### Parameters

- `ts` (`java.time.Instant`)

#### Throws

- `NullPointerException` — if ts is null
