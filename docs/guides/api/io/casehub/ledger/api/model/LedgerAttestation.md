# io.casehub.ledger.api.model.LedgerAttestation

**Package:** `io.casehub.ledger.api.model`

**Kind:** `class`

A peer attestation stamped onto a `LedgerEntry`.

<p>Carries either a binary verdict (`verdict`) for trust scoring, or a continuous
quality score (`dimensionScore` ∈ [0.0, 1.0]) for dimension-labelled scoring when
`trustDimension` is set. Both fields may be populated together.

## Fields

### `attestorId` (`java.lang.String`)

### `attestorRole` (`java.lang.String`)

### `attestorType` (`ActorType`)

### `capabilityTag` (`java.lang.String`)

### `confidence` (`double`)

### `dimensionScore` (`java.lang.Double`)

Continuous quality score in [0.0, 1.0]. Only meaningful when `trustDimension` is
set. Null on ordinary (binary verdict) attestations.

### `evidence` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `ledgerEntryId` (`java.util.UUID`)

### `occurredAt` (`java.time.Instant`)

### `subjectId` (`java.util.UUID`)

### `trustDimension` (`java.lang.String`)

Application-defined quality dimension label (e.g. `"review-thoroughness"`). Null on ordinary attestations.

### `verdict` (`io.casehub.ledger.api.model.AttestationVerdict`)

## Constructors

### `public LedgerAttestation()`
