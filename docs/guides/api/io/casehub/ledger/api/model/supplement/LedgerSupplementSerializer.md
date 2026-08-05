# io.casehub.ledger.api.model.supplement.LedgerSupplementSerializer

**Package:** `io.casehub.ledger.api.model.supplement`

**Kind:** `class`

Serialises a list of `LedgerSupplement` instances to a compact JSON string
for storage in the `supplement_json` column of `ledger_entry`.

<p>
Each supplement is serialised under its type key (`"COMPLIANCE"`,
`"PROVENANCE"`, `"OBSERVABILITY"`). Null fields are omitted.
Returns `null` when the list is null or empty — preserving a null
`supplement_json` for entries that carry no supplements.

<p>
This class is not a CDI bean — it is a pure static utility with no Quarkus
runtime dependency. It can be used in unit tests without a running container.

## Fields

### `MAPPER` (`ObjectMapper`)

## Constructors

### `private LedgerSupplementSerializer()`

## Methods

### `private static void putIfNotNull(java.util.Map<java.lang.String,java.lang.Object> map, java.lang.String key, java.lang.Object value)`

#### Parameters

- `map` (`java.util.Map<java.lang.String,java.lang.Object>`)
- `key` (`java.lang.String`)
- `value` (`java.lang.Object`)

### `private static java.util.Map<java.lang.String,java.lang.Object> toFieldMap(io.casehub.ledger.api.model.supplement.LedgerSupplement supplement)`

#### Parameters

- `supplement` (`io.casehub.ledger.api.model.supplement.LedgerSupplement`)

### `public static java.lang.String toJson(java.util.List<io.casehub.ledger.api.model.supplement.LedgerSupplement> supplements)`

Serialise a list of supplements to a JSON string.

#### Parameters

- `supplements` (`java.util.List<io.casehub.ledger.api.model.supplement.LedgerSupplement>`) — the supplements to serialise; may be null or empty

#### Returns

a JSON string, or `null` if the list is null or empty

### `private static java.lang.String typeKey(io.casehub.ledger.api.model.supplement.LedgerSupplement supplement)`

#### Parameters

- `supplement` (`io.casehub.ledger.api.model.supplement.LedgerSupplement`)
