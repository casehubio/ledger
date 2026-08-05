# io.casehub.ledger.api.spi.TrustScoreSource

**Package:** `io.casehub.ledger.api.spi`

**Kind:** `interface`

Source of trust scores for an actor. Abstracts whether scores are read from a materialized
store, an in-memory cache, or computed on demand from raw attestation history.

<p>All implementations must agree on the empty-actor contract: when no EVENT entries exist
for the actor, score methods return `OptionalDouble.empty()` and `decisionCount`
returns 0.

<p>Batch methods (`.scoresFor` and `.decisionCountsFor`) default to a per-actor
loop. Implementations backed by a persistent store should override with a single
`WHERE actorId IN (...)` query to avoid N round-trips.

## Methods

### `public abstract java.util.Map<java.lang.String,java.lang.Double> allCapabilityScores(java.lang.String actorId)`

#### Parameters

- `actorId` (`java.lang.String`)

### `public abstract java.util.Map<java.lang.String,java.lang.Double> allDimensionScores(java.lang.String actorId)`

#### Parameters

- `actorId` (`java.lang.String`)

### `public abstract java.util.OptionalDouble capabilityDimensionScore(java.lang.String actorId, java.lang.String capabilityTag, java.lang.String dimensionKey)`

#### Parameters

- `actorId` (`java.lang.String`)
- `capabilityTag` (`java.lang.String`)
- `dimensionKey` (`java.lang.String`)

### `public abstract java.util.OptionalDouble capabilityScore(java.lang.String actorId, java.lang.String capabilityTag)`

#### Parameters

- `actorId` (`java.lang.String`)
- `capabilityTag` (`java.lang.String`)

### `public abstract int decisionCount(java.lang.String actorId, java.lang.String capabilityTag)`

Returns 0 when no trust history exists for the actor+capability pair.

#### Parameters

- `actorId` (`java.lang.String`)
- `capabilityTag` (`java.lang.String`)

### `public default java.util.Map<java.lang.String,java.lang.Integer> decisionCountsFor(java.util.List<java.lang.String> candidateIds, java.lang.String capabilityTag)`

Batch decision counts for multiple candidates against the same capability tag.

<p>Every candidate appears in the result map. 0 means no history (BOOTSTRAP phase).
Ordering follows `candidateIds` insertion order.

#### Parameters

- `candidateIds` (`java.util.List<java.lang.String>`) — actor IDs to query; empty list returns an empty map
- `capabilityTag` (`java.lang.String`) — the capability being evaluated

#### Returns

map from actorId → decision count; all candidates present

### `public abstract java.util.OptionalDouble dimensionScore(java.lang.String actorId, java.lang.String dimensionKey)`

#### Parameters

- `actorId` (`java.lang.String`)
- `dimensionKey` (`java.lang.String`)

### `public abstract java.util.OptionalDouble globalScore(java.lang.String actorId)`

#### Parameters

- `actorId` (`java.lang.String`)

### `public abstract java.util.Map<java.lang.String,java.lang.Double> qualityScores(java.lang.String actorId, java.lang.String capabilityTag)`

#### Parameters

- `actorId` (`java.lang.String`)
- `capabilityTag` (`java.lang.String`)

### `public default java.util.Map<java.lang.String,java.util.OptionalDouble> scoresFor(java.util.List<java.lang.String> candidateIds, java.lang.String capabilityTag)`

Batch capability scores for multiple candidates against the same capability tag.

<p>Every candidate appears in the result map. `OptionalDouble.empty()` means no
score has been computed yet — the actor is in the BOOTSTRAP phase for this capability.

<p>Ordering follows `candidateIds` insertion order. Implementations backed by a
persistent store should override with a single `IN (...)` query.

#### Parameters

- `candidateIds` (`java.util.List<java.lang.String>`) — actor IDs to score; empty list returns an empty map
- `capabilityTag` (`java.lang.String`) — the capability being evaluated

#### Returns

map from actorId → score; all candidates present, never null values
