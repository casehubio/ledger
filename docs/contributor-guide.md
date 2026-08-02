# casehub-ledger — Contributor Guide

> Internal architecture, services, SPIs, and extension points for platform builders modifying casehub-ledger.

**GitHub:** [casehubio/casehub-ledger](https://github.com/casehubio/casehub-ledger)

---

## Module Structure

| Module | Artifact ID | Purpose |
|--------|-------------|---------|
| `api/` | `casehub-ledger-api` | Pure-Java SPIs and model types — no JPA, no Quarkus framework deps. Two-tier `LedgerEntry` model: `@MappedSuperclass` base in `api/`, `JpaLedgerEntry` entity in `runtime/`. `LedgerAppender` SPI for domain-agnostic ledger writes with `AuditRecord` value type. |
| `runtime/` | `casehub-ledger` | Full extension: JPA entities, services, Flyway migrations, CDI. |
| `deployment/` | `casehub-ledger-deployment` | Quarkus build-time augmentation. |
| `persistence-memory/` | `casehub-ledger-memory` | Zero-datasource in-memory `@Alternative @Priority(1)` implementations of all persistence SPIs — for `@QuarkusTest` isolation and ephemeral installs. |
| `rest/` | `casehub-ledger-rest` | JAX-RS REST API for ledger queries and admin operations — opt-in via explicit dependency. |
| `testing/` | `casehub-ledger-testing` | NoOp SPI implementations for test isolation. |
| `consumer-compat-test/` | `casehub-ledger-consumer-compat-test` | Boot guard for CDI graph integrity. Standalone POM (not a child of ledger parent). |
| `signing/` | `casehub-ledger-signing` | Reactor POM for cloud-managed Ed25519 signing adapters. Each provider has a pure Java module (framework-free) and a Quarkus CDI adapter module. |

---

## Services Internals

### Trust Score Architecture

On-read computation via `TrustScoreSource` SPI — three implementations:
- **`MaterializedTrustScoreSource`** (`@DefaultBean`) — reads pre-computed scores from `ActorTrustScoreRepository` (original path). Overrides batch methods (`scoresFor`, `decisionCountsFor`) with single `WHERE actorId IN (...)` queries.
- **`CachedTrustScoreSource`** — wraps `MaterializedTrustScoreSource` with in-memory TTL cache; replaces the engine-side `TrustScoreCache`.
- **`ComputedTrustScoreSource`** — computes on demand via `TrustScoreCalculator` (pure computation extracted from `PerActorTrustComputer`).

### Incremental Recomputation

When `casehub.ledger.trust-score.incremental.enabled=true` (default false), `saveAttestation()` fires `AttestationRecordedEvent` -> `IncrementalTrustUpdateObserver` (AFTER_SUCCESS + REQUIRES_NEW) -> `PerActorTrustComputer` recomputes the affected actor's scores immediately using the same Bayesian Beta algorithm as the batch job. Fires `TrustScoreActorUpdatedEvent` on completion. The nightly `TrustScoreJob` remains as a consistency backstop.

### TrustGateService API

Injects `TrustScoreSource` (not `ActorTrustScoreRepository`); returns `OptionalDouble` (was `Optional<Double>`); `findScore()` removed; `dimensionScores()` renamed to `allDimensionScores()`. `TrustGateService.allCapabilityScores(String actorId): Map<String, Double>` — returns all CAPABILITY-scoped trust scores as a capability-tag to score map.

### Batch Capability Scoring

`TrustScoreSource` defines two batch default methods — `scoresFor(List<String> candidateIds, String capabilityTag) -> Map<String, OptionalDouble>` and `decisionCountsFor(...) -> Map<String, Integer>`. Defaults loop per-actor; `MaterializedTrustScoreSource` overrides with single `IN (...)` queries. Every candidate appears in the result map; `OptionalDouble.empty()` means the actor is in the BOOTSTRAP phase.

### Content-Aware Merkle Leaf Hash

`LedgerMerkleTree.leafHash()` computes `SHA-256(0x00 | entry.canonicalBytes())`. `canonicalBytes()` includes all core fields (subjectId, sequenceNumber, entryType, actorId, actorRole, occurredAt, tenancyId, actorType, causedByEntryId) plus `metadata`, `supplementJson`, and `domainContentBytes()`. Subclasses with persistent join-table fields MUST override `domainContentBytes()` — build-time enforcement via `LedgerProcessor` produces a deployment error if they do not.

### @CrossTenant Qualifier

CDI qualifier (`io.casehub.ledger.runtime.qualifier`) disambiguating `CrossTenantLedgerEntryRepository` / `CrossTenantReactiveLedgerEntryRepository` from their tenant-scoped counterparts. Unqualified injection fails at startup. Not applied to inherently cross-tenant repos (`ActorTrustScoreRepository`, `KeyRotationRepository`, `ActorIdentityBindingRepository`). Build-time enforcement: `LedgerProcessor` rejects `@RequestScoped` beans injecting `@CrossTenant`. TenancyId propagates through the CDI event chain via `LedgerEntry.tenancyId`, set at persist time by the repository.

### LedgerEnricherPipeline

`@ApplicationScoped` CDI bean that owns enricher pipeline execution — shared by the JPA `@EntityListeners` path and the in-memory path. It is not an SPI (consumers do not implement it) but is the shared execution point for any consumer that adds enrichers.

### ReactiveAgentIdentityVerificationService

`@DefaultBean @Unremovable` Mutiny bridge wrapping `AgentIdentityVerificationService` on the blocking worker pool. Always active regardless of `reactive.enabled`.

### ErasureReceiptLedgerEntry

GDPR Art.17 tamper-evident erasure receipt (V1010). Opt-in via `casehub.ledger.erasure-receipt.enabled=true` (default false). `subjectId=nameUUIDFromBytes(erasedActorId)`. `ErasureReason` enum: `GDPR_ART_17_REQUEST | RETENTION_EXPIRED | ACCOUNT_DELETION`. `ErasureResult` now carries `Optional<UUID> receiptEntryId`. Activate `JpaErasureReceiptRepository @Alternative` via `quarkus.arc.selected-alternatives`.

### LedgerSequenceAllocator

Dialect detection now queries `INFORMATION_SCHEMA.SETTINGS` to detect `H2 MODE=PostgreSQL` (previously used `getMetaData().getURL()` which Agroal strips). Plain H2 (no `MODE=PostgreSQL`) gets the SQL-standard `MERGE` path; PostgreSQL and `H2+MODE=PostgreSQL` get `ON CONFLICT DO NOTHING`. Fixes `casehub-engine-ledger` and any downstream module using plain H2 tests.

### LedgerPrivacyProducer

Now injects `Instance<EntityManager>` instead of `EntityManager` directly — datasource-free deployments (casehub-drafthouse, casehub-qhorus without ledger JPA) no longer fail CDI augmentation on `ActorIdentityProvider`.

### @NamedQuery Migration

All JPQL queries migrated from inline strings to `@NamedQuery` annotations on `JpaLedgerEntry`. 11 named queries: `LedgerEntry.listAll`, `findAllEvents`, `findEventsByActorId`, `findByTimeRange`, `findByIdAndTenancyId`, `findSequenceStats`, `findBySubjectId`, `findBySubjectIdAndTimeRange`, `findLatestBySubjectId`, `findByActorIdAndTimeRange`, `findByActorRoleAndTimeRange`, `findCausedBy`.

---

## Cloud KMS Signers

Cloud-managed Ed25519 signing lives in the `signing/` reactor. Each provider has a **pure Java module** (no framework deps — usable from `main()`) and a **Quarkus CDI adapter** module. The Vault module defines the `VaultTokenSource` SPI; cloud KMS modules use their own provider-native credential mechanisms.

| Pure Java Module | Quarkus Adapter | Cloud Provider | Key Classes |
|------------------|-----------------|----------------|-------------|
| `signing/vault-transit` | `signing/vault-transit-quarkus` | HashiCorp Vault Transit | `VaultTransitSigningClient`, `VaultTokenSource` SPI, `AppRoleVaultTokenSource`, `JwtVaultTokenSource`, `StaticVaultTokenSource`, `LoginBasedVaultTokenSource` |
| `signing/aws-kms` | `signing/aws-kms-quarkus` | AWS KMS | `AwsKmsSigningClient`, `AwsKmsContext`, `AwsKmsSigningConfig` |
| `signing/gcp-kms` | `signing/gcp-kms-quarkus` | GCP Cloud KMS | `GcpKmsSigningClient`, `GcpKmsClientWrapper`, `GcpKmsContext` |
| `signing/azure-keyvault` | `signing/azure-keyvault-quarkus` | Azure Key Vault | `AzureKeyVaultSigningClient`, `AzureKeyVaultClientWrapper`, `EcSignatureConverter` |

**`VaultTokenSource` SPI** (`io.casehub.ledger.signing.vault`): `token()` and `invalidate()`. Three implementations extend `LoginBasedVaultTokenSource` (abstract — lazy login with lease-aware TTL, 30s buffer before expiry): `AppRoleVaultTokenSource`, `JwtVaultTokenSource` (consolidates Kubernetes auth — accepts any JWT source including OIDC, federated identity), `StaticVaultTokenSource` (constant token, no-op invalidate).

**403-retry:** `VaultTransitAgentSigner` (Quarkus adapter) catches `VaultAuthenticationException` on both `fetchPublicKey()` and `sign()`, calls `tokenSource.invalidate()`, obtains a fresh token, and retries once. `VaultTransitSigningClient` throws `VaultAuthenticationException` on HTTP 403.

---

## Dependencies

### Depends On

Nothing in the casehubio ecosystem. Quarkus + Hibernate ORM only.

### Depended On By

| Repo | How |
|---|---|
| `casehub-work` | Optional ledger module — extends `LedgerEntry` to record work item events |
| `casehub-qhorus` | Mandatory — extends `LedgerEntry` to record agent messages; provides ledger write integration |
| `casehub-engine` | Optional ledger module — extends `LedgerEntry` to record case events |
| `claudony` | Transitively via Qhorus and casehub-ledger |

---

## Current State

- All modules on main: api, runtime, deployment, persistence-memory, rest, testing, consumer-compat-test, signing (8 sub-modules)
- 962 tests passing, native image validated
- Reactive/blocking service parity enforced at build time via `BlockingReactiveParityTest` (ArchUnit 1.4.1) — auto-discovers all `Reactive*Service` classes and asserts bidirectional method parity and `Uni<T>` returns
- All epics complete: MMR, PROV-DM, privacy/pseudonymisation, EigenTrust, trust routing signals, OTel auto-wiring
- No deployed production instances — schema migrations can be rewritten in place (no incremental migration scripts needed)
- Quarkiverse submission pending (eligibility discussion ongoing)

---

## Design Documents

- [docs/DESIGN.md](https://raw.githubusercontent.com/casehubio/casehub-ledger/main/docs/DESIGN.md) — full architecture, agent identity model, mesh topology decisions
- [docs/CAPABILITIES.md](https://raw.githubusercontent.com/casehubio/casehub-ledger/main/docs/CAPABILITIES.md) — capability applicability ratings and selection matrix
- [adr/INDEX.md](https://raw.githubusercontent.com/casehubio/casehub-ledger/main/adr/INDEX.md) — architectural decision records
