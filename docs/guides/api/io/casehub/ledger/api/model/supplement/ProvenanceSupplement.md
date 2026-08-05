# io.casehub.ledger.api.model.supplement.ProvenanceSupplement

**Package:** `io.casehub.ledger.api.model.supplement`

**Kind:** `class`

Supplement carrying workflow provenance — the external entity that originated
this ledger entry's subject — and optional LLM agent configuration binding.

<p>
Use this supplement when a subject is created or driven by an external workflow
system (e.g. a `quarkus-flow` workflow instance). The three source fields
identify the source entity precisely enough to correlate across systems:

<pre>`JpaProvenanceSupplement ps = new JpaProvenanceSupplement();
ps.sourceEntityId = workflowInstance.id.toString();
ps.sourceEntityType = "Flow:WorkflowInstance";
ps.sourceEntitySystem = "quarkus-flow";
entry.attach(ps);`</pre>

<p>
For LLM agent entries, also populate `agentConfigHash` with the SHA-256
of the agent's configuration (e.g. CLAUDE.md + system prompts) to enable
configuration drift detection within a persona version. This field does not
affect trust scoring — it is a forensic audit field only. See ADR 0004.

## Fields

### `agentConfigHash` (`java.lang.String`)

SHA-256 hex digest of the LLM agent's configuration at session start
(e.g. `sha256(CLAUDE.md + system-prompts)`). Nullable — only populated
for entries produced by LLM agents. Used for configuration drift detection;
not the trust key (trust accumulates on `actorId`). See ADR 0004.

### `sourceEntityId` (`java.lang.String`)

Identifier of the external entity that originated this subject.

### `sourceEntitySystem` (`java.lang.String`)

The system that owns the external entity.
Example: `"quarkus-flow"`, `"casehub-work"`.

### `sourceEntityType` (`java.lang.String`)

Type of the external entity.
Convention: `"System:TypeName"`, e.g. `"Flow:WorkflowInstance"`.

## Constructors

### `public ProvenanceSupplement()`
