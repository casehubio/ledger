# casehub-ledger — Consumer Guide

> Domain-agnostic, immutable, cryptographically tamper-evident audit ledger for any Quarkus application.

**GitHub:** [casehubio/casehub-ledger](https://github.com/casehubio/casehub-ledger)
**Tier:** Foundation

---

## Purpose

Zero knowledge of business domain. Consumers extend it; it never extends them. Any Quarkus app adds `io.casehub:casehub-ledger` as a dependency and immediately gets:

- Immutable append-only audit log (`LedgerEntry` base entity with JPA JOINED inheritance)
- Merkle Mountain Range tamper evidence (RFC 9162 stored frontier — O(log N) inclusion proofs, Ed25519 signed checkpoints)
- Peer attestation (`LedgerAttestation` — verdicts, confidence scores)
- EigenTrust reputation (`TrustScoreComputer` — nightly batch, exponential decay weighting)
- Provenance tracking (`sourceEntityId / sourceEntityType / sourceEntitySystem`)
- Decision context snapshots (GDPR Article 22 / EU AI Act Article 12 compliance)

---

## Modules to Depend On

| Module | Artifact ID | When to use |
|--------|-------------|-------------|
| `api/` | `casehub-ledger-api` | Pure-Java SPIs and model types — no JPA, no Quarkus framework deps. Two-tier `LedgerEntry` model: `@MappedSuperclass` base in `api/`, `JpaLedgerEntry` entity in `runtime/`. |
| `runtime/` | `casehub-ledger` | Full extension: JPA entities, services, Flyway migrations, CDI. |
| `deployment/` | `casehub-ledger-deployment` | Quarkus build-time augmentation. |
| `persistence-memory/` | `casehub-ledger-memory` | Zero-datasource in-memory `@Alternative @Priority(1)` implementations of all persistence SPIs — for `@QuarkusTest` isolation and ephemeral installs. Add as `compile`-scope dependency to activate. |
| `rest/` | `casehub-ledger-rest` | JAX-RS REST API for ledger queries and admin operations — opt-in via explicit dependency. Provides `GET /ledger/entries`, `GET /ledger/entries/{id}`, `POST /ledger/entries/search` with pagination, filtering, and type discrimination. |
| `testing/` | `casehub-ledger-testing` | NoOp SPI implementations for test isolation — `NoOpLedgerAppender`, `NoOpActorIdentityProvider`, `NoOpTrustScoreSource`, etc. |
| `consumer-compat-test/` | `casehub-ledger-consumer-compat-test` | Boot guard for CDI graph integrity. Standalone POM (not a child of ledger parent). Single `@QuarkusTest` with empty body — if CDI boots with no persistence infrastructure and no `quarkus.arc.exclude-types`, every injection point is satisfied by `@DefaultBean` no-ops. `maven.deploy.skip=true`. |

---

## Key Abstractions

### Core Model

| Concept | Role |
|---|---|
| `LedgerEntry` | Abstract base entity for all tamper-evident audit records |
| `LedgerAttestation` | Peer verdict record (SOUND / FLAGGED / ENDORSED / CHALLENGED) with confidence and evidence |
| `ActorTrustScore` | Per-actor trust score keyed by actor, capability, and dimension; supports Bayesian and continuous score types |
| `LedgerMerkleFrontier` | Stored MMR frontier enabling incremental Merkle tree operations per subject |
| `ActorIdentity` | Token-to-identity mapping for pseudonymisation |

### SPIs (Consumer-Implemented or Built-In Alternatives)

| SPI | Default | Built-in Alternative | Purpose |
|---|---|---|---|
| `LedgerEntryRepository` / `ReactiveLedgerEntryRepository` | — | JPA default; `InMemoryLedgerEntryRepository @Alternative @Priority(1)` in `casehub-ledger-memory` | Persistence for ledger entries |
| `LedgerMerkleFrontierRepository` | — | JPA default (`JpaLedgerMerkleFrontierRepository @Alternative`) | Read/replace the per-subject Merkle MMR frontier |
| `ActorTrustScoreRepository` | — | JPA default | Persistence for trust scores |
| `TrustScoreSource` | `MaterializedTrustScoreSource` | `CachedTrustScoreSource`, `ComputedTrustScoreSource` | On-read trust score retrieval; injected into `TrustGateService` |
| `ActorIdentityProvider` | — | JPA default | Tokenise / resolve / erase actor identities (GDPR) |
| `ErasureReceiptRepository` | — | JPA default (`JpaErasureReceiptRepository @Alternative`); `NoOpErasureReceiptRepository` | Query erasure receipt entries by actor/tenant |
| `DecisionContextSanitiser` | no-op | — | Sanitise PII from decision context JSON before storage |
| `LedgerTraceIdProvider` | OTel span | — | Override OTel trace ID extraction |
| `TrustImportService` | no-op default | JPA default (seed-if-absent) | Import trust scores from external payload |
| `TrustBootstrapSource` | no-op default | — | Fetch prior trust data for first-time actors |
| `ActorDIDProvider` | — | `ScimActorDIDProvider @Alternative @Priority(1)` (explicit activation via `quarkus.arc.selected-alternatives`) | Resolves actorId to DID via SCIM2 Agent endpoint |

### Supplements (Optional Attachments)

| Supplement | Purpose |
|---|---|
| `ComplianceSupplement` | GDPR Art.22 / EU AI Act Art.12 decision fields |
| `ProvenanceSupplement` | Data lineage — source entity, workflow reference |

### Metadata Field

`LedgerEntry.metadata` — consumer-provided freeform JSON context (`TEXT` column). Included in `canonicalBytes()` for tamper evidence. Must be valid JSON, must NOT contain PII. Propagated through the write path via `AuditRecord.withMetadata(String)` and `OutcomeRecord.withMetadata(String)`. Config: `casehub.ledger.metadata.max-size` (default 65536).

---

## Consumer Pattern

How to extend the ledger for your domain:

1. **Extend `LedgerEntry`** as a JPA `@Entity` with `@DiscriminatorValue`
2. **Add a Flyway migration** (V1011+ range) for the subclass join table
3. **Wire a CDI observer** to capture domain events as ledger entries
4. **Optionally attach** `ComplianceSupplement` or `ProvenanceSupplement`

### Existing Consumers

| Consumer | Subclass | Subclass table | subject_id maps to |
|---|---|---|---|
| `casehub-work` | `WorkItemLedgerEntry` | `work_item_ledger_entry` | WorkItem UUID |
| `casehub-qhorus` | `MessageLedgerEntry` | `message_ledger_entry` | Channel UUID |

### Leaf Hash Requirement

Subclasses with persistent join-table fields MUST override `domainContentBytes()` — build-time enforcement via `LedgerProcessor` produces a deployment error if they do not. The leaf hash is `SHA-256(0x00 | canonicalBytes)` per RFC 9162.

---

## Flyway Conventions

Path: `classpath:db/ledger/migration` (moved from `classpath:db/migration` in ledger#95).
Consumers must add this path to their `quarkus.flyway.locations` config.

| Version | Contents |
|---|---|
| V1000 | `ledger_entry` + `ledger_attestation` tables |
| V1001 | `actor_trust_score` table |
| V1002 | Supplement tables |
| V1003 | `ledger_entry_archive` table |
| V1004 | `actor_identity` pseudonymisation table |
| V1005 | `agent_signature` + `agent_public_key` columns on `ledger_entry` |
| V1006 | `agent_key_ref` column on `ledger_entry` |
| V1007 | `key_rotation_entry` subclass table |
| V1009 | `plain_ledger_entry` — `PlainLedgerEntry` for domain-agnostic event writes (`OutcomeRecorder`) |
| V1010 | `erasure_receipt_entry` — `ErasureReceiptLedgerEntry` (opt-in via `casehub.ledger.erasure-receipt.enabled=true`) |

**Consumers** own V1011+ for their own subclass join tables (V1004-V1010 are ledger base).

---

## Agent Identity Convention

Format: `{model-family}:{persona}@{major}` — e.g. `"claude:tarkus-reviewer@v1"`.
Major version bump resets trust baseline to Beta(1,1) = 0.5 prior.
Bump criteria: model family change, persona behaviour change, scope change. Do NOT bump for: bug fixes, tuning, CLAUDE.md changes that don't alter behaviour.

---

## Configuration

### Agent Identity / SCIM

Config prefix: `casehub.ledger.agent-identity.scim.*`

| Key | Purpose |
|-----|---------|
| `endpoint` | SCIM2 Agent endpoint URL |
| `auth-token` | Bearer token for the SCIM2 endpoint |
| `timeout-ms` | HTTP request timeout |
| `cache-ttl-minutes` | TTL for the per-actorId DID cache |
| `require-https` | Reject non-HTTPS endpoint URLs |

### Trust Score

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.trust-score.incremental.enabled` | `false` | Enable immediate per-actor recomputation on attestation |
| `casehub.ledger.trust-score.materialization.enabled` | `true` | When `false`, skip batch/incremental persistence — `ComputedTrustScoreSource` still works |

### Erasure

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.erasure-receipt.enabled` | `false` | Opt-in tamper-evident GDPR Art.17 erasure receipts |

### Metadata

| Key | Default | Purpose |
|-----|---------|---------|
| `casehub.ledger.metadata.max-size` | `65536` | Max size for consumer-provided JSON metadata |

---

## What This Repo Does NOT Do

- Provide REST endpoints by default — `ledger-rest` is opt-in via explicit dependency (consumers may still define their own)
- Provide MCP tools (consumers define their own)
- Capture domain events (consumers wire their own `@ObservesAsync` observers)
- Replay events or project CQRS views
- Know anything about WorkItems, Cases, or agent channels

---

## Boundary Rules

- `casehub-ledger` provides model, SPI, services, and JPA implementations only
- Domain-specific subclasses, REST endpoints, and MCP tools live in consumers
- `subjectId` is the generic aggregate identifier — consumers set it to their own aggregate UUID (WorkItem UUID, Channel UUID, etc.)
- All queries, sequences, and hash chains are scoped per `subjectId`
- Multi-tenancy uses explicit `tenancyId` parameter on every tenant-scoped SPI method; filtering is unconditional
