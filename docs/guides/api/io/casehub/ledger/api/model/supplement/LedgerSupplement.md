# io.casehub.ledger.api.model.supplement.LedgerSupplement

**Package:** `io.casehub.ledger.api.model.supplement`

**Kind:** `class`

Abstract base for all ledger supplements.

<p>
A <strong>supplement</strong> is an optional, lazily-loaded extension to a
`LedgerEntry` that carries a named group of cross-cutting fields. Supplements
exist in separate tables and are never written unless the consumer explicitly
attaches one — consumers that do not use supplements incur zero schema or runtime cost.

<p>
Two built-in supplements are provided:
<ul>
<li>`ComplianceSupplement` — GDPR Art.22 decision snapshot, EU AI Act Art.12,
governance reference, rationale</li>
<li>`ProvenanceSupplement` — workflow source entity</li>
</ul>

<p>
Supplements are accessed via the typed helper methods on `LedgerEntry`:
`entry.compliance()` and `entry.provenance()`.
Use `entry.attach(supplement)` to add or replace a supplement; this also
keeps `entry.supplementJson` in sync automatically.

<p>
<strong>Zero-complexity guarantee:</strong> If a consumer never calls
`entry.attach()`, no supplement table rows are written and the lazy
`supplements` list is never initialised.

<p>
This class is `@MappedSuperclass` — it defines the common column mappings
inherited by all JPA supplement entities. The `ledgerEntry` back-reference
is `@Transient` at this level; JPA subclasses add the concrete
`@ManyToOne` relationship.

## Fields

### `id` (`java.util.UUID`)

Primary key — UUID assigned on first persist.

### `supplementType` (`java.lang.String`)

Discriminator value — identifies the supplement type.
Use `instanceof` checks or `LedgerEntry.compliance()` etc.
for typed access rather than reading this field directly.

## Constructors

### `public LedgerSupplement()`
