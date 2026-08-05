# io.casehub.ledger.api.model.KeyRotationReason

**Package:** `io.casehub.ledger.api.model`

**Kind:** `enum`

Why an agent signing key was rotated.

<p>
Per NIST SP 800-57, key rotation (planned) and key revocation (compromise)
are distinct lifecycle events requiring distinct records and response procedures.

## Enum Constants

### `COMPROMISED` (`io.casehub.ledger.api.model.KeyRotationReason`)

Emergency rotation because the key was leaked or the actor was misbehaving.
Entries signed by the previous key on or after `effectiveSince` are SUSPECT.

### `SCHEDULED` (`io.casehub.ledger.api.model.KeyRotationReason`)

Planned rotation due to cryptoperiod policy — no compromise assumed.
Entries signed by the previous key remain VALID.

## Constructors

### `private KeyRotationReason()`

## Methods

### `public static io.casehub.ledger.api.model.KeyRotationReason valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ledger.api.model.KeyRotationReason[] values()`
