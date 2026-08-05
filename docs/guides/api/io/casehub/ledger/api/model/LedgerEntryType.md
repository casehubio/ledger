# io.casehub.ledger.api.model.LedgerEntryType

**Package:** `io.casehub.ledger.api.model`

**Kind:** `enum`

Classifies a `LedgerEntry` as an intent, a fact, or a peer review record.

## Enum Constants

### `ATTESTATION` (`io.casehub.ledger.api.model.LedgerEntryType`)

A peer attestation stamped onto an existing entry.

### `COMMAND` (`io.casehub.ledger.api.model.LedgerEntryType`)

The actor's declared intent before execution — e.g. "ApproveWorkItem".

### `EVENT` (`io.casehub.ledger.api.model.LedgerEntryType`)

The observable fact after execution — e.g. "WorkItemApproved".

## Constructors

### `private LedgerEntryType()`

## Methods

### `public static io.casehub.ledger.api.model.LedgerEntryType valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ledger.api.model.LedgerEntryType[] values()`
