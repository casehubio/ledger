# io.casehub.ledger.api.model.AttestationVerdict

**Package:** `io.casehub.ledger.api.model`

**Kind:** `enum`

The formal verdict recorded in a `LedgerAttestation`.

<p>
SOUND and ENDORSED are treated as positive signals in EigenTrust reputation computation.
FLAGGED and CHALLENGED are treated as negative signals.

## Enum Constants

### `CHALLENGED` (`io.casehub.ledger.api.model.AttestationVerdict`)

The attested entry is formally disputed by the attestor. Negative signal.

### `ENDORSED` (`io.casehub.ledger.api.model.AttestationVerdict`)

The attested entry is explicitly endorsed by the attestor. Positive signal.

### `FLAGGED` (`io.casehub.ledger.api.model.AttestationVerdict`)

The attested entry needs review or contains concerns. Negative signal.

### `SOUND` (`io.casehub.ledger.api.model.AttestationVerdict`)

The attested entry is correct and complete. Positive signal.

## Constructors

### `private AttestationVerdict()`

## Methods

### `public static io.casehub.ledger.api.model.AttestationVerdict valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.ledger.api.model.AttestationVerdict[] values()`
