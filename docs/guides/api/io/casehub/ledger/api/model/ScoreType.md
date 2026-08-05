# io.casehub.ledger.api.model.ScoreType

**Package:** `io.casehub.ledger.api.model`

**Kind:** `enum`

Score type discriminator — determines which key columns are non-null in ActorTrustScore.

## Enum Constants

### `CAPABILITY` (`io.casehub.ledger.api.model.ScoreType`)

Capability-scoped binary trust. capability_key is set; dimension_key is null. See ADR 0008.

### `CAPABILITY_DIMENSION` (`io.casehub.ledger.api.model.ScoreType`)

Per-capability quality dimension. Both capability_key and dimension_key are set. See #76.

### `DIMENSION` (`io.casehub.ledger.api.model.ScoreType`)

Cross-capability quality dimension. capability_key is null; dimension_key is set. See #62.

### `GLOBAL` (`io.casehub.ledger.api.model.ScoreType`)

Classic cross-decision score. capability_key and dimension_key are null.

## Constructors

### `private ScoreType()`

## Methods

### `public static io.casehub.ledger.api.model.ScoreType valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ledger.api.model.ScoreType[] values()`
