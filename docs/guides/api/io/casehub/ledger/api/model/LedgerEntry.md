# io.casehub.ledger.api.model.LedgerEntry

**Package:** `io.casehub.ledger.api.model`

**Kind:** `class`

Abstract base for all ledger entries.

<h2>Core fields</h2>
<p>
`LedgerEntry` holds exactly the fields that are relevant for every entry,
every consumer, every time: the subject aggregate, sequence position, actor identity,
timestamp, and the tamper-evident hash chain. Nothing else.

<h2>Supplements</h2>
<p>
Optional cross-cutting concerns are handled by
`io.casehub.ledger.api.model.supplement.LedgerSupplement` subclasses
attached via `.attach(LedgerSupplement)`:
<ul>
<li>`ComplianceSupplement` — GDPR Art.22 decision snapshot, governance</li>
<li>`ProvenanceSupplement` — workflow source entity</li>
</ul>
If a consumer never calls `attach()`, no supplement tables are written
and the lazy `supplements` list is never initialised — zero overhead.

<h2>JPA JOINED inheritance</h2>
<p>
Domain-specific subclasses (e.g. `WorkItemLedgerEntry` in Tarkus) extend
the JPA entity subclass and add a sibling table joined on `id`. Supplements
are orthogonal to subclasses — any subclass can attach any supplement.

<h2>Hash chain</h2>
<p>
The `digest` field holds the RFC 9162 leaf hash — `SHA-256(0x00 | canonical fields)`.
Chain integrity is maintained by the Merkle Mountain Range in `LedgerMerkleFrontier`.

<h2>Two-tier design</h2>
<p>
This class is `@MappedSuperclass` — it defines all persistent fields and the
canonical bytes computation. JPA-specific machinery (`@Entity`, `@Inheritance`,
`@NamedQuery`, `@EntityListeners`) lives on the runtime
`JpaLedgerEntry` subclass. Non-JPA backends (in-memory, event-sourced) extend
this class directly.

## Fields

### `EMPTY_BYTES` (`byte[]`)

### `actorDid` (`java.lang.String`)

DID URI bound to this entry's actorId at write time. Null when no binding is configured.

### `actorId` (`java.lang.String`)

Identity of the actor who triggered this transition.

### `actorRole` (`java.lang.String`)

The functional role of the actor in this transition — e.g. `"Resolver"`.

### `actorType` (`ActorType`)

Whether the actor is a human, autonomous agent, or the system itself.

### `agentKeyRef` (`java.lang.String`)

Self-derived identifier for the key generation that produced `.agentSignature`.
Value: `Base64URL(SHA-256(agentPublicKey))` — computable from stored bytes.
Null when `.agentSignature` is null.

### `agentPublicKey` (`byte[]`)

X.509-encoded Ed25519 public key of the signing agent.
Stored alongside the signature for self-contained verification —
entries remain verifiable without any external key management system.
Null when `.agentSignature` is null.

### `agentSignature` (`byte[]`)

Ed25519 signature of `.canonicalBytes()`
by the agent identified in `.actorId`.
Null when the actor is not configured for bilateral signing.

### `causedByEntryId` (`java.util.UUID`)

FK to the ledger entry that causally produced this entry.
Null for entries with no known causal predecessor.

<p>
Enables cross-system causal chain traversal via
`LedgerEntryRepository#findCausedBy(UUID)`.
When Claudony orchestrates Tarkus → Qhorus, each downstream entry's
`causedByEntryId` points to its upstream cause.

### `digest` (`java.lang.String`)

RFC 9162 leaf hash: `SHA-256(0x00 | canonicalFields)`.
Null when hash chain is disabled (`casehub.ledger.hash-chain.enabled=false`).

### `entryType` (`io.casehub.ledger.api.model.LedgerEntryType`)

Whether this entry is a command (intent), event (fact), or attestation record.

### `id` (`java.util.UUID`)

Primary key — UUID assigned eagerly at construction time.

### `metadata` (`java.lang.String`)

Consumer-provided freeform JSON context for this entry.

<p>Carries domain-specific audit data (routing rationale, candidate lists,
decision explanations) that is opaque to the ledger. Stored verbatim,
included in `.canonicalBytes()` for tamper evidence, returned on reads.

<p><strong>Contract:</strong> Must be valid JSON. Must NOT contain personally
identifiable information (PII) — the GDPR Art.17 erasure mechanism severs
the token→identity mapping but does not scan or modify field contents.

### `occurredAt` (`java.time.Instant`)

When this entry was recorded — set automatically on first persist.

### `sequenceNumber` (`int`)

Position of this entry in the per-subject ledger sequence (1-based).

### `subjectId` (`java.util.UUID`)

The aggregate this entry belongs to — the domain object whose lifecycle
is being recorded. Scopes the sequence number and hash chain.

### `supplementJson` (`java.lang.String`)

Denormalised JSON snapshot of all attached supplements.
Written automatically by `.attach(LedgerSupplement)`.
Enables fast single-entry reads without joining supplement tables.
Format: `{"COMPLIANCE":{...`,"PROVENANCE":{...}}}.

### `supplements` (`java.util.List<io.casehub.ledger.api.model.supplement.LedgerSupplement>`)

In-memory supplements attached to this entry.
Marked `@Transient` — JPA supplement entities are persisted explicitly
by the save pipeline (each supplement type has its own self-contained table).
Use `.attach(LedgerSupplement)`, `.compliance()`,
and `.provenance()` for type-safe access.

### `tenancyId` (`java.lang.String`)

Tenant that owns this entry. Non-null, defaults to
`io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID`.
Set at persist time by the repository — callers do not set this directly.

### `traceId` (`java.lang.String`)

OpenTelemetry trace ID linking this entry to a distributed trace.
W3C trace context format (32-char hex string).

## Constructors

### `public LedgerEntry()`

## Methods

### `public void attach(io.casehub.ledger.api.model.supplement.LedgerSupplement supplement)`

Attach a supplement to this entry, replacing any existing supplement of the
same type. Also refreshes `.supplementJson` to keep it in sync.

<p>
<strong>Important:</strong> After attaching, do not mutate the supplement's fields
directly without calling `.refreshSupplementJson()` — direct field mutation
leaves `supplementJson` stale. Prefer re-attaching a new supplement instance
when fields need to change.

#### Parameters

- `supplement` (`io.casehub.ledger.api.model.supplement.LedgerSupplement`) — the supplement to attach; must not be null

### `public final byte[] canonicalBytes()`

Compute the canonical byte representation of this entry for tamper-evident hashing.

<p>
Includes all tamper-critical fields:
<ul>
<li>Base identity: `subjectId`, `sequenceNumber`, `entryType`</li>
<li>Actor context: `actorId`, `actorRole`, `actorType`</li>
<li>Timing: `occurredAt` (truncated to milliseconds)</li>
<li>Multi-tenancy: `tenancyId`</li>
<li>Causality: `causedByEntryId`</li>
<li>Consumer metadata: `metadata` (always present — empty string when null)</li>
<li>Supplements: `supplementJson` (if non-null)</li>
<li>Domain content: `domainContentBytes()` (if non-empty)</li>
</ul>

<p>
Format: 10 pipe-delimited positional base fields, followed by optional supplement JSON
and domain content:
`subjectId|seqNum|entryType|actorId|actorRole|occurredAt|tenancyId|actorType|causedByEntryId|metadata[|supplementJson][|domainContent]`

<p>
Null fields are rendered as empty strings. Deterministic — same entry produces same bytes.
`metadata` is positional (always present) to avoid ambiguity with the conditionally
appended `supplementJson`.

#### Returns

canonical UTF-8 byte array for this entry

### `public java.util.Optional<io.casehub.ledger.api.model.supplement.ComplianceSupplement> compliance()`

Returns the `ComplianceSupplement` attached to this entry, if any.

#### Returns

the compliance supplement, or empty if none is attached

### `protected byte[] domainContentBytes()`

Returns domain-specific content bytes for hash protection.

<p>Subclasses that declare persistent fields on join tables MUST override
this method to include those fields. The returned bytes are appended to the
canonical form used by both the Merkle leaf hash and the agent signature.

<p>Build-time enforcement: `LedgerProcessor` produces a deployment error
if a `LedgerEntry` subclass declares persistent fields (non-`@Transient`)
but does not override this method.

#### Returns

domain content bytes; empty array if no domain fields exist

### `public java.util.Optional<io.casehub.ledger.api.model.supplement.ProvenanceSupplement> provenance()`

Returns the `ProvenanceSupplement` attached to this entry, if any.

#### Returns

the provenance supplement, or empty if none is attached

### `public void refreshSupplementJson()`

Refreshes `.supplementJson` from the current state of the
`.supplements` list.

<p>
Call this after mutating a supplement's fields in-place (e.g. when adding
`rationale` to an already-attached `ComplianceSupplement`):

<pre>`entry.compliance().ifPresent(cs -> {
    cs.rationale = reason;
    entry.refreshSupplementJson();`);
}</pre>
