# io.casehub.ledger.api.model.ErasureReason

**Package:** `io.casehub.ledger.api.model`

**Kind:** `enum`

Why a GDPR Art.17 erasure was performed.

<p>Stored on `io.casehub.ledger.runtime.model.ErasureReceiptLedgerEntry`
to make the legal basis for each erasure event queryable and auditable.

## Enum Constants

### `ACCOUNT_DELETION` (`io.casehub.ledger.api.model.ErasureReason`)

Account deletion triggered erasure of all associated ledger identity mappings.

### `GDPR_ART_17_REQUEST` (`io.casehub.ledger.api.model.ErasureReason`)

Subject exercised the Art.17 right-to-erasure.

### `RETENTION_EXPIRED` (`io.casehub.ledger.api.model.ErasureReason`)

Automated retention policy expired the actor's data window.

## Constructors

### `private ErasureReason()`

## Methods

### `public static io.casehub.ledger.api.model.ErasureReason valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ledger.api.model.ErasureReason[] values()`
