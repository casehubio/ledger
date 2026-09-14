# casehub-ledger -- Contributor Guide

> Internal architecture, services, SPIs, and extension points for platform builders modifying casehub-ledger.

**GitHub:** [casehubio/casehub-ledger](https://github.com/casehubio/casehub-ledger)

---

## Module Structure

| Module | Artifact ID | Purpose |
|--------|-------------|---------|
| `api/` | `casehub-ledger-api` | Pure-Java SPIs and model types -- no JPA, no Quarkus framework deps. Two-tier `LedgerEntry` model: `@MappedSuperclass` base in `api/`, `JpaLedgerEntry` entity in `runtime/`. Contains `LedgerAppender`, `OutcomeRecorder`, `LedgerEntryRepository`, `TrustScoreSource`, `ActorIdentityProvider`, `LedgerTraceIdProvider` SPIs and the `AuditRecord` / `OutcomeRecord` value types. Enums: `LedgerEntryType`, `AttestationVerdict`, `ScoreType`, `CapabilityTag`, `ErasureReason`, `KeyRotationReason`. Supplements: `ComplianceSupplement`, `ProvenanceSupplement` (`@MappedSuperclass`). |
| `runtime/` | `casehub-ledger` | Full Quarkus extension: JPA entities (`JpaLedgerEntry`, `PlainLedgerEntry`, `KeyRotationEntry`, `ActorIdentityBindingEntry`, `ErasureReceiptLedgerEntry`, `ActorTrustScore`, `LedgerMerkleFrontier`, `ActorIdentity`, `LedgerEntryArchiveRecord`), all services, repositories, enrichers, privacy, federation, routing, config, and Flyway migrations. |
| `deployment/` | `casehub-ledger-deployment` | Quarkus build-time augmentation. `LedgerProcessor` enforces `domainContentBytes()` override on subclasses with persistent fields and rejects `@RequestScoped` beans injecting `@CrossTenant`. |
| `persistence-memory/` | `casehub-ledger-memory` | Zero-datasource in-memory `@Alternative @Priority(1)` implementations of all persistence SPIs -- for `@QuarkusTest` isolation and ephemeral installs. |
| `rest/` | `casehub-ledger-rest` | JAX-RS REST API for ledger queries, attestations, Merkle verification, and trust scores -- opt-in via explicit dependency. Base path: `/api/v1/ledger/`. Four resource classes: `LedgerEntryResource`, `AttestationResource`, `MerkleVerificationResource`, `TrustScoreResource`. |
| `testing/` | `casehub-ledger-testing` | `NoOpLedgerEntryRepository` -- `@Alternative @Priority(1)` for consumer test isolation. |
| `annotations/` | `casehub-ledger-annotations` / `casehub-ledger-annotations-deployment` | Annotation-driven audit (`@Audited`, `@Attested`, `@ComplianceSupplement`). Quarkus extension with build-time validation via `LedgerAnnotationsProcessor`. Interceptors: `AuditedInterceptor` (APPLICATION+1), `ComplianceSupplementInterceptor` (APPLICATION). |
| `graphql/` | `casehub-ledger-graphql` | GraphQL resolvers (`LedgerQueryResolver`, `LedgerMutationResolver`) and MCP domain provider (`LedgerModelEnricher`). DTOs decoupled from JPA entities. |
| `signing/` | (reactor POM) | Cloud-managed Ed25519 signing adapters. 8 sub-modules (4 pure Java + 4 Quarkus CDI adapters). |
| `examples/` | (reactor POM) | 14 runnable example applications. Not deployed. |
| `consumer-compat-test/` | `casehub-ledger-consumer-compat-test` | Boot guard for CDI graph integrity. Standalone POM (not a child of ledger parent). |

---

## Architecture Overview

### Threading Model

All services are blocking, running on the Quarkus worker thread pool. The reactive tier was retired in #180/#182 (July 2026) -- virtual thread alignment means Quarkus handles non-blocking dispatch at the framework level. No `Reactive*` classes, no `Uni<T>` return types exist in the codebase.

### Save Pipeline

The entry persistence pipeline runs in this exact order:

1. **Enrichment** -- `LedgerEnricherPipeline` invokes all `LedgerEntryEnricher` implementations in `@Priority` order
2. **Digest** -- `LedgerMerkleTree.leafHash()` computes `SHA-256(0x00 | entry.canonicalBytes())`
3. **Agent Signature** -- `AgentEntrySigner` calls `AgentSigner.sign()` for the entry's actorId
4. **Persist** -- `LedgerEntryRepository.save()` with sequence allocation and Merkle frontier update

This order is critical: enrichers run before hashing, so any field they set is included in the tamper-evident digest. Agent signature covers the same `canonicalBytes()`.

### CDI Bean Graph

`@DefaultBean` no-op implementations exist for every persistence SPI. Consumers without a datasource boot successfully -- every injection point is satisfied. The `consumer-compat-test` module verifies this invariant.

JPA implementations are `@Alternative` beans activated by the presence of a datasource (Quarkus auto-discovers them when the datasource is configured). The `persistence-memory` module provides `@Alternative @Priority(1)` beans that win over JPA alternatives in test contexts.

NoOp implementations in the `runtime` module (not `@Alternative`, just `@DefaultBean`):
- `NoOpLedgerEntryRepository`
- `NoOpLedgerMerkleFrontierRepository`
- `NoOpActorTrustScoreRepository`
- `NoOpErasureReceiptRepository`
- `NoOpActorIdentityBindingRepository`

---

## Services Internals

### Trust Score Architecture

On-read computation via `TrustScoreSource` SPI -- three implementations:
- **`MaterializedTrustScoreSource`** (`@DefaultBean`) -- reads pre-computed scores from `ActorTrustScoreRepository` (original path). Overrides batch methods (`scoresFor`, `decisionCountsFor`) with single `WHERE actorId IN (...)` queries.
- **`CachedTrustScoreSource`** -- wraps `MaterializedTrustScoreSource` with in-memory TTL cache.
- **`ComputedTrustScoreSource`** -- computes on demand via `TrustScoreCalculator` (pure computation extracted from `TrustScoreComputer`). Works regardless of `materialization.enabled`.

### TrustScoreCalculator

Pure Java -- no CDI, no database. Suitable for unit tests without Quarkus. Algorithm: start with prior Beta(1, 1). For each attestation across all of an actor's decisions, compute a decay weight via the injected `DecayFunction` using the attestation's `occurredAt`. SOUND/ENDORSED increments alpha by `decayWeight * confidence`; FLAGGED/CHALLENGED increments beta by `decayWeight * confidence`. Score = alpha/(alpha+beta), clamped to [0.0, 1.0].

Default `ExponentialDecayFunction` applies exponential decay with an asymmetric valence multiplier -- FLAGGED attestations decay slower via `casehub.ledger.decay.flagged-persistence-multiplier` (default 0.5 = FLAGGED persists twice as long as SOUND).

### PerActorTrustComputer

`@ApplicationScoped` package-private CDI bean that orchestrates trust computation for a single actor. Called by both `TrustScoreJob` (batch) and `IncrementalTrustUpdateObserver` (per-attestation). Delegates pure computation to `TrustScoreCalculator`, persists results via `ActorTrustScoreRepository.upsert()`, and captures `TrustScoreSnapshot` records for all four score types on every computation.

### Incremental Recomputation

When `casehub.ledger.trust-score.incremental.enabled=true` (default false):
1. `JpaLedgerEntryRepository.saveAttestation()` fires `AttestationRecordedEvent`
2. `IncrementalTrustUpdateObserver` (CDI observer, `AFTER_SUCCESS` transaction phase, `REQUIRES_NEW`) picks it up
3. `TrustScoreComputer` recomputes the affected actor's scores immediately
4. Fires `TrustScoreActorUpdatedEvent` on completion

The nightly `TrustScoreJob` remains as a consistency backstop.

### TrustGateService API

Policy layer on top of `TrustScoreSource`. Consumers call this instead of querying the source directly -- threshold checks and CAPABILITY-to-GLOBAL fallback logic stays in one place.

Key methods:
- `meetsThreshold(actorId, minTrust)` -- global score check
- `meetsThreshold(actorId, capabilityTag, minTrust)` -- capability score with GLOBAL fallback
- `meetsQualityThreshold(actorId, capabilityTag, dimension, minScore)` -- dimension score check
- `currentScore(actorId)` / `currentScore(actorId, capabilityTag)` -- raw score access (returns `OptionalDouble`)
- `allCapabilityScores(actorId)` / `allDimensionScores(actorId)` -- all scores as `Map<String, Double>`
- `decisionCount(actorId, capabilityTag)` -- number of recorded decisions
- `scoresFor(candidateIds, capabilityTag)` -- batch capability scores (`Map<String, OptionalDouble>`)
- `decisionCountsFor(candidateIds, capabilityTag)` -- batch decision counts (`Map<String, Integer>`)

### Batch Capability Scoring

`TrustScoreSource` defines two batch default methods -- `scoresFor(List<String>, String) -> Map<String, OptionalDouble>` and `decisionCountsFor(...) -> Map<String, Integer>`. Defaults loop per-actor; `MaterializedTrustScoreSource` overrides with single `IN (...)` queries. Every candidate appears in the result map; `OptionalDouble.empty()` means the actor is in the BOOTSTRAP phase.

### Attestation Aggregation

`AttestationAggregator` consolidates multiple attestations on the same entry into a single signal for trust scoring. Three strategies:
- `WEIGHTED_MAJORITY` (default) -- confidence-weighted vote; higher total weighted confidence wins.
- `UNANIMOUS_REQUIRED` -- any FLAGGED/CHALLENGED produces FLAGGED consensus.
- `FIRST_ATTESTOR` -- uses only the first attestation (single-attestation pass-through).

Configured via `casehub.ledger.trust-score.aggregation-strategy`.

### EigenTrust Power Iteration

When `casehub.ledger.trust-score.eigentrust.enabled=true`, EigenTrust runs after the Bayesian Beta pass to compute transitive global trust scores across the agent mesh. Configuration:
- `alpha` (dampening constant, default 0.15)
- `pre-trusted-actors` (list of unconditionally trusted actor IDs that seed the eigenvector)

### Content-Aware Merkle Leaf Hash

`LedgerMerkleTree.leafHash()` computes `SHA-256(0x00 | entry.canonicalBytes())`. `canonicalBytes()` includes all core fields: `subjectId`, `sequenceNumber`, `entryType`, `actorId`, `actorRole`, `occurredAt` (truncated to millis), `tenancyId`, `actorType`, `causedByEntryId`, `metadata` -- plus `supplementJson` and `domainContentBytes()` when non-empty.

Format: 10 pipe-delimited positional base fields, followed by optional supplement JSON and domain content:
`subjectId|seqNum|entryType|actorId|actorRole|occurredAt|tenancyId|actorType|causedByEntryId|metadata[|supplementJson][|domainContent]`

Subclasses with persistent join-table fields MUST override `domainContentBytes()` -- build-time enforcement via `LedgerProcessor` produces a deployment error if they do not.

### @CrossTenant Qualifier

CDI qualifier (`io.casehub.ledger.runtime.qualifier`) disambiguating `CrossTenantLedgerEntryRepository` from the tenant-scoped `LedgerEntryRepository`. Unqualified injection of `CrossTenantLedgerEntryRepository` fails at startup. Not applied to inherently cross-tenant repos (`ActorTrustScoreRepository`, `KeyRotationRepository`, `ActorIdentityBindingRepository`). Build-time enforcement: `LedgerProcessor` rejects `@RequestScoped` beans injecting `@CrossTenant`. TenancyId propagates through the CDI event chain via `LedgerEntry.tenancyId`, set at persist time by the repository.

### LedgerEnricherPipeline

`@ApplicationScoped` CDI bean that owns enricher pipeline execution -- shared by the JPA `@EntityListeners` path and the in-memory path. It is not an SPI (consumers do not implement it) but is the shared execution point. Enrichers are CDI-discovered via `@Inject @Any Instance<LedgerEntryEnricher>` and invoked in ascending `@Priority` order. Exceptions are logged and swallowed -- enrichers must never block persistence.

Built-in enrichers:

| Priority | Class | Package | Purpose |
|---|---|---|---|
| 10 | `TraceIdEnricher` | `runtime.service` | Populates `traceId` from `LedgerTraceIdProvider` |
| 30 | `ProvenanceCaptureEnricher` | `runtime.service.intercept` | Attaches `ProvenanceSupplement` from CDI context |
| 35 | `ComplianceSupplementEnricher` | `runtime.service.intercept` | Attaches `ComplianceSupplement` from ThreadLocal context |
| 40 | `ActorDIDEnricher` | `runtime.service.identity` | Populates `actorDid` from platform `ActorDIDProvider` |
| 50 | `ActorIdentityValidationEnricher` | `runtime.service.identity` | Fires DID/VC identity validation; records `ActorIdentityBindingEntry` |

### AgentIdentityVerificationService

Ledger adapter wrapping the platform `AgentIdentityVerificationService`. Extracts `actorId`, `actorDid`, `agentPublicKey` from a `LedgerEntry` and delegates to the domain-agnostic platform service.

### LedgerErasureService

CDI bean for GDPR Art.17 erasure requests. Severs the token-to-identity mapping via `ActorIdentityProvider.erase()`. When `casehub.ledger.erasure-receipt.enabled=true`, writes a tamper-evident `ErasureReceiptLedgerEntry` to the Merkle chain in the same transaction. Returns an `ErasureResult` with diagnostic information and optional receipt entry ID.

### LedgerSequenceAllocator

Handles per-(subject, tenant) sequence number allocation. Dialect detection queries `INFORMATION_SCHEMA.SETTINGS` to detect `H2 MODE=PostgreSQL` (previously used `getMetaData().getURL()` which Agroal strips). Plain H2 (no `MODE=PostgreSQL`) gets the SQL-standard `MERGE` path; PostgreSQL and `H2+MODE=PostgreSQL` get `ON CONFLICT DO NOTHING`. This ensures correct concurrent sequence allocation across all test and production environments.

### LedgerPrivacyProducer

Injects `Instance<EntityManager>` instead of `EntityManager` directly -- datasource-free deployments (e.g. casehub-drafthouse, casehub-qhorus without ledger JPA) no longer fail CDI augmentation on `ActorIdentityProvider`.

### LedgerHealthJob

Periodic sequence gap detection and reconciliation. Opt-in via `@IfBuildProperty("casehub.ledger.health.enabled")` -- prevents crash in consumer apps without ledger schema. Per-check transaction boundaries at scale. Uses `CrossTenantLedgerEntryRepository.findSequenceStats()` for efficient gap detection.

### @NamedQuery Convention

All JPQL queries are `@NamedQuery` annotations on the entity classes. Inline query strings are not used. Key named queries on `JpaLedgerEntry`:
- `LedgerEntry.listAll`, `findAllEvents`, `findEventsByActorId`
- `findByTimeRange`, `findByIdAndTenancyId`, `findSequenceStats`
- `findBySubjectId`, `findBySubjectIdAndTimeRange`, `findLatestBySubjectId`
- `findByActorIdAndTimeRange`, `findByActorRoleAndTimeRange`, `findCausedBy`

---

## Repository Architecture

### Tenant-Scoped Repositories

All repository SPI methods take `tenancyId` as a parameter. Filtering is unconditional -- there is no "skip tenant filter" option.

| Repository | SPI Location | JPA Implementation | In-Memory Implementation |
|---|---|---|---|
| `LedgerEntryRepository` | api | `JpaLedgerEntryRepository` | `InMemoryLedgerEntryRepository` |
| `LedgerMerkleFrontierRepository` | runtime | `JpaLedgerMerkleFrontierRepository` | `InMemoryLedgerMerkleFrontierRepository` |
| `ErasureReceiptRepository` | runtime | `JpaErasureReceiptRepository` | `InMemoryErasureReceiptRepository` |
| `ActorIdentityBindingRepository` | runtime | `JpaActorIdentityBindingRepository` | `InMemoryActorIdentityBindingRepository` |

### Cross-Tenant Repositories

| Repository | SPI Location | JPA Implementation | In-Memory Implementation | Notes |
|---|---|---|---|---|
| `CrossTenantLedgerEntryRepository` | runtime | `JpaCrossTenantLedgerEntryRepository` | `InMemoryCrossTenantLedgerEntryRepository` | Requires `@CrossTenant` qualifier |

### Unscoped Repositories (Inherently Cross-Tenant)

| Repository | SPI Location | JPA Implementation | In-Memory Implementation | Notes |
|---|---|---|---|---|
| `ActorTrustScoreRepository` | runtime | `JpaActorTrustScoreRepository` | `InMemoryActorTrustScoreRepository` | Trust scores are actor-global |
| `KeyRotationRepository` | runtime | `JpaKeyRotationRepository` | `InMemoryKeyRotationRepository` | `findByActorId()` is tenant-scoped; `findCompromisedByActorIdAndKeyRef()` is cross-tenant |
| `TrustScoreSnapshotRepository` | runtime | `JpaTrustScoreSnapshotRepository` | `InMemoryTrustScoreSnapshotRepository` | Trust score trajectory snapshots. `findGlobalSnapshots`, `findCapabilitySnapshots`, `findDimensionSnapshots`, `findByActorAndTimeRange`, `deleteOlderThan`. Retention trimming via `casehub.ledger.trust-score.snapshot.retention-days`. |

---

## Cloud KMS Signers

Cloud-managed Ed25519 signing lives in the `signing/` reactor. Each provider has a **pure Java module** (no framework deps -- usable from `main()`) and a **Quarkus CDI adapter** module that implements `AgentSigner`.

| Pure Java Module | Quarkus Adapter | Cloud Provider | Key Classes |
|---|---|---|---|
| `signing/vault-transit` | `signing/vault-transit-quarkus` | HashiCorp Vault Transit | `VaultTransitSigningClient`, `VaultTransitContext`, `VaultTransitSigningConfig`, `VaultTokenSource` SPI, `LoginBasedVaultTokenSource`, `AppRoleVaultTokenSource`, `JwtVaultTokenSource`, `StaticVaultTokenSource`, `VaultAuthenticationException`, `VaultTransitAgentSigner` |
| `signing/aws-kms` | `signing/aws-kms-quarkus` | AWS KMS | `AwsKmsSigningClient`, `AwsKmsContext`, `AwsKmsSigningConfig`, `AwsKmsKeyInfo`, `AwsKmsAgentSigner`, `AwsKmsConfig` |
| `signing/gcp-kms` | `signing/gcp-kms-quarkus` | GCP Cloud KMS | `GcpKmsSigningClient`, `GcpKmsClientWrapper`, `DefaultGcpKmsClientWrapper`, `GcpKmsContext`, `GcpKmsAgentSigner`, `GcpKmsConfig` |
| `signing/azure-keyvault` | `signing/azure-keyvault-quarkus` | Azure Key Vault | `AzureKeyVaultSigningClient`, `AzureKeyVaultClientWrapper`, `DefaultAzureKeyVaultClientWrapper`, `AzureKeyVaultContext`, `EcSignatureConverter`, `AzureKeyVaultAgentSigner`, `AzureKeyVaultConfig` |

### VaultTokenSource SPI

`io.casehub.ledger.signing.vault.VaultTokenSource`: `token()` and `invalidate()`. Three implementations extend `LoginBasedVaultTokenSource` (abstract -- lazy login with lease-aware TTL, 30s buffer before expiry):
- `AppRoleVaultTokenSource` -- Vault AppRole auth
- `JwtVaultTokenSource` -- consolidates Kubernetes auth; accepts any JWT source including OIDC, federated identity
- `StaticVaultTokenSource` -- constant token, no-op invalidate

### 403-Retry Protocol

`VaultTransitAgentSigner` (Quarkus adapter) catches `VaultAuthenticationException` on both `fetchPublicKey()` and `sign()`, calls `tokenSource.invalidate()`, obtains a fresh token, and retries once. `VaultTransitSigningClient` throws `VaultAuthenticationException` on HTTP 403.

### AbstractCachingAgentSigner

Base class for Quarkus CDI adapter signers. Caches public key material per actorId to avoid redundant cloud KMS calls. Subclasses override `performSign()` and `fetchPublicKey()`. The `keyMaterial()` method returns cached key without triggering a sign operation.

---

## Federation Services

### Trust Export

`TrustExportService` produces `TrustExportPayload` containing all computed trust scores from `ActorTrustScoreRepository`. Optional `deploymentId` (config: `casehub.ledger.trust-score.export.deployment-id`) is included so importers can filter out their own exports.

### Trust Import

`TrustImportService` SPI with two implementations:
- `NoOpTrustImportService` (`@DefaultBean`) -- no-op
- `JpaTrustImportService` (`@Alternative`) -- seed-if-absent for all score types

### Trust Bootstrap

`TrustBootstrapService` runs as a batch pre-pass at the start of each `TrustScoreJob` run (when `casehub.ledger.trust-score.bootstrap.enabled=true`). For actors with no existing trust score, calls `TrustBootstrapSource.fetchPriorTrust()` and imports via `TrustImportService`.

`TrustBootstrapSource` SPI:
- `NoOpTrustBootstrapSource` (`@DefaultBean`) -- returns empty, preserving Beta(1,1) uninformative prior

---

## Trust Routing Events

When `casehub.ledger.trust-score.routing-enabled=true`, `TrustScoreRoutingPublisher` fires CDI events after trust score computation:

| Event | When | Payload |
|---|---|---|
| `TrustScoreDeltaPayload` | After batch recomputation | Only actors whose score changed by more than `routing-delta-threshold` |
| `TrustScoreFullPayload` | After batch recomputation | Complete score snapshot |
| `TrustScoreActorUpdatedEvent` | After incremental recomputation | Single actor's updated scores |

---

## Privacy Architecture

### Pseudonymisation Flow

1. `LedgerEntryRepository.save()` calls `ActorIdentityProvider.tokenise(rawActorId, actorType)`
2. Only `ActorType.HUMAN` actors (and null actorType as safe default) are tokenised
3. `ActorType.SYSTEM` and `ActorType.AGENT` actors pass through unchanged (not natural persons)
4. On read queries, `ActorIdentityProvider.tokeniseForQuery()` returns the stored key without creating mappings

### Erasure Flow

1. `LedgerErasureService.erase(rawActorId, tenancyId, reason)` severs the token-to-identity mapping
2. Ledger entries retaining the token become permanently anonymous
3. If `casehub.ledger.erasure-receipt.enabled=true`, writes `ErasureReceiptLedgerEntry` (part of Merkle chain)
4. `ErasureResult` carries `Optional<UUID> receiptEntryId`

### Decision Context Sanitisation

`DecisionContextSanitiser` SPI (runtime): sanitise PII from `ComplianceSupplement.decisionContext` JSON before persist. Default: `PassThroughDecisionContextSanitiser` (no-op). Replace with custom CDI bean to strip PII.

---

## REST Module Internals

Four resource classes, all under `/api/v1/ledger/`:

| Resource | Path | Methods |
|---|---|---|
| `LedgerEntryResource` | `/entries` | `GET /entries` (query by subject/actor + time range), `GET /entries/{id}`, `GET /entries/{id}/caused-by` |
| `AttestationResource` | `/entries/{entryId}/attestations` | `GET` (list, optional capabilityTag filter), `POST` (create attestation) |
| `MerkleVerificationResource` | `/verify` | `GET /verify` (verify all entries for subject), `GET /verify/entries/{entryId}/proof` (inclusion proof) |
| `TrustScoreResource` | `/trust` | `GET /trust/{actorId}` (all scores), `GET /trust/{actorId}/capability/{capabilityTag}` (capability-specific) |

DTO mapping via `LedgerDtoMapper`. Response types: `LedgerEntryResponse`, `AttestationResponse`, `VerificationResponse`, `InclusionProofResponse`, `TrustScoreResponse`, `CapabilityScoreResponse`. Error handling via `LedgerExceptionMapper` and `LedgerNotFoundException`.

All endpoints require `tenancyId` as a query parameter (validated by `LedgerRestUtil.requireTenancyId()`).

---

## Deployment Processor

`LedgerProcessor` (in `deployment/`) performs build-time validation:

1. **domainContentBytes() guard** -- any `LedgerEntry` subclass that declares persistent fields (non-`@Transient`) on a join table must override `domainContentBytes()`. Failure produces a deployment error.
2. **@CrossTenant scope guard** -- `@RequestScoped` beans injecting `@CrossTenant` repositories are rejected at build time.

---

## Dependencies

### Depends On

Nothing in the casehubio ecosystem beyond platform libraries. Quarkus + Hibernate ORM + `casehub-platform-api` (for `ActorType`, `TenancyConstants`) + `casehub-platform-identity` (for `ActorDIDProvider`, `DIDResolver`, `AgentCredentialValidator`, `IdentityVerificationResult`, `IdentityBindingStatus`, `CredentialValidationResult`, and no-op defaults).

### Depended On By

| Repo | How |
|---|---|
| `casehub-work` | Optional ledger module -- extends `LedgerEntry` to record work item events |
| `casehub-qhorus` | Mandatory -- extends `LedgerEntry` to record agent messages; provides ledger write integration |
| `casehub-engine` | Optional ledger module -- extends `LedgerEntry` to record case events |
| `claudony` | Transitively via Qhorus and casehub-ledger |

---

## Flyway Migration Registry

Path: `classpath:db/ledger/migration`.

All base schema is consolidated into a single migration file. No production database exists -- schema is maintained as a clean-slate migration.

| Version | Contents |
|---|---|
| V1000 | All ledger tables: `ledger_entry`, `ledger_attestation`, `actor_trust_score`, `compliance_supplement`, `provenance_supplement`, `ledger_entry_archive`, `actor_identity`, `key_rotation_entry`, `actor_identity_binding_entry`, `plain_ledger_entry`, `erasure_receipt_entry`, `trust_score_snapshot`, `ledger_merkle_frontier`, `ledger_subject_sequence`. |

Consumer subclass tables start at V1012+.

---

## Current State

- All modules on main: api, runtime, deployment, persistence-memory, annotations (runtime + deployment), rest, graphql, testing, consumer-compat-test, signing (8 sub-modules), examples (14 sub-modules)
- Reactive tier fully retired (#180, #182) -- all services are blocking, virtual-thread-aligned
- All JPQL queries migrated to `@NamedQuery` (#179, #154)
- All epics complete: MMR, PROV-DM, privacy/pseudonymisation, EigenTrust, trust routing signals, OTel auto-wiring, multi-tenancy, cloud KMS signing, REST API, metadata field
- No deployed production instances -- schema migrations can be rewritten in place
- Quarkiverse submission pending

### Open Issues

| # | Title | Status |
|---|---|---|
| 205 | Full consumer and contributor guide review -- drift, gaps, staleness | Open |
| 178 | Field-level GDPR erasure for metadata containing PII | Open (deferred -- only needed if PII-free contract proves insufficient) |
| 171 | Vault browser-based OIDC auth flow (two-step auth URL + callback) | Open (deferred -- not needed until interactive admin tooling exists) |
| 96 | Code-generation approach for reactive service tier | Open (deferred -- reactive retired; only relevant if reactive pair count grows) |

---

## Design Documents

- `ARC42STORIES.MD` -- **primary architecture record** (arc42 + story progression). Covers entity model, architecture, SPI contracts, Merkle MMR, trust scoring, agent identity, and delivery history.
- `docs/DESIGN.md` -- redirects to `ARC42STORIES.MD`
- `docs/CAPABILITIES.md` -- redirects to `ARC42STORIES.MD`
- `docs/adr/INDEX.md` -- architectural decision records (17 ADRs)
- `docs/specs/` -- design specs (60+ specs from brainstorming sessions)
