# casehub-ledger-annotations — @Audited, @ComplianceSupplement, @Attested

**Date:** 2026-08-18
**Status:** Approved
**Scope:** New `casehub-ledger-annotations` module (runtime + deployment)
**Issue:** casehubio/ledger#195
**Epic:** casehubio/blocks#115 (annotation-driven agent programming model)

## Motivation

CaseHub's annotation-driven model (blocks#115) gives each repo a `*-annotations` module. Ledger's contribution is governance annotations that compose onto any CDI method — recording audit entries, attaching compliance metadata, and writing attestations. These are cross-cutting concerns that work with any orchestration pattern (LC4j, CaseHub engine, plain CDI).

The ledger already has the `@ProvenanceCapture` CDI interceptor pattern proving that interceptor bindings + enrichers work for transparent ledger integration. This module follows the same architecture for auditing.

## Module Structure

New `annotations/` directory under the ledger repo root:

```
annotations/
├── pom.xml                              (aggregator: casehub-ledger-annotations-parent)
├── runtime/
│   ├── pom.xml                          (casehub-ledger-annotations)
│   └── src/main/java/io/casehub/ledger/annotations/
│       ├── Audited.java                 — @InterceptorBinding
│       ├── Attested.java                — plain annotation
│       ├── ComplianceSupplement.java    — plain annotation (same name as api model class, different package: io.casehub.ledger.annotations vs io.casehub.ledger.api.model.supplement)
│       ├── SubjectId.java               — @Target(PARAMETER)
│       ├── ActorId.java                 — @Target(PARAMETER)
│       ├── DecisionContext.java         — @Target(PARAMETER)
│       ├── ConfidenceScore.java         — @Target(PARAMETER)
│       ├── TenancyId.java              — @Target(PARAMETER)
│       ├── Verdict.java                — @Target(PARAMETER)
│       └── runtime/
│           ├── AuditedInterceptor.java
│           ├── ComplianceSupplementContext.java
│           └── ComplianceSupplementEnricher.java
└── deployment/
    ├── pom.xml                          (casehub-ledger-annotations-deployment)
    └── src/main/java/.../deployment/
        └── LedgerAnnotationsProcessor.java
```

### Dependencies

**Runtime module:**
- `casehub-ledger-api` — AuditRecord, OutcomeRecord, LedgerAppender, OutcomeRecorder, LedgerEntryType, AttestationVerdict
- `casehub-platform-api` — CurrentPrincipal, ActorType
- `quarkus-core` — CDI, interceptors
- `quarkus-arc` — interceptor registration

**Deployment module:**
- `quarkus-core-deployment` — BuildStep, Jandex
- runtime module

**No langchain4j-agentic dependency.** Ledger annotations are pure governance annotations that compose onto any CDI method. Cross-module composition with LC4j happens in the blocks build extension (casehub-blocks-engine-adapter), not via a direct ledger dependency.

### Maven coordinates

| Element | Value |
|---------|-------|
| Parent artifactId | `casehub-ledger-annotations-parent` |
| Runtime artifactId | `casehub-ledger-annotations` |
| Deployment artifactId | `casehub-ledger-annotations-deployment` |
| Package | `io.casehub.ledger.annotations` |
| Runtime subpackage | `io.casehub.ledger.annotations.runtime` |
| Deployment subpackage | `io.casehub.ledger.annotations.deployment` |

## Annotation Definitions

### `@Audited` — CDI interceptor binding

```java
@InterceptorBinding
@Inherited
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    @Nonbinding String actorRole() default "";
    @Nonbinding LedgerEntryType entryType() default LedgerEntryType.EVENT;
    @Nonbinding boolean auditFailures() default false;
}
```

Marks a CDI method for automatic ledger entry recording. On successful method execution, the interceptor creates an `AuditRecord` and calls `LedgerAppender.append()`.

- `actorRole` — actor's role in this operation (e.g., "reviewer", "approver")
- `entryType` — EVENT (default, "this happened") or COMMAND ("this was requested")
- `auditFailures` — when true, also records entries for failed method executions

**No `capabilityTag` on `@Audited`:** `capabilityTag` is an attestation-level concept — it exists on `LedgerAttestation`, not `LedgerEntry`. Use `@Attested(capabilityTag = "...")` for capability-scoped recording.

### `@Attested` — attestation composition

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Attested {
    AttestationVerdict verdict() default AttestationVerdict.SOUND;
    double confidence() default -1.0;  // sentinel — must be overridden by attribute or @ConfidenceScore param
    String capabilityTag();
}
```

Composes with `@Audited` to create both a ledger entry AND an attestation in one transaction via `OutcomeRecorder`. Cannot be used without `@Audited` (build-time validation error).

- `verdict` — default SOUND, overridden by `@Verdict` parameter annotation when the verdict is dynamic
- `confidence` — default sentinel (-1.0), overridden by `@ConfidenceScore` parameter annotation or explicit attribute value. Build-time error if neither is set.
- `capabilityTag` — capability scope for the attestation (required — OutcomeRecord rejects null)

**Dynamic verdict/confidence:** Static annotation values are defaults. Parameter annotations override them at runtime — a review method that returns SOUND or FLAGGED depending on findings uses `@Verdict` to express this:

```java
@Audited
@Attested(capabilityTag = "review")
ReviewResult review(
        @SubjectId UUID caseId,
        @Verdict AttestationVerdict verdict,
        @ConfidenceScore double confidence,
        String submission) { ... }
```

### `@ComplianceSupplement` — EU AI Act / GDPR metadata

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ComplianceSupplement {
    String algorithmRef() default "";
    String contestationUri() default "";
    boolean humanOverrideAvailable() default false;
    String planRef() default "";
    String rationale() default "";
}
```

Attaches compliance metadata to the ledger entry. Static fields come from annotation attributes. Dynamic fields (`confidenceScore`, `decisionContext`) come from parameter annotations.

### Parameter annotations

All `@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.RUNTIME)`:

| Annotation | Type | Required | Purpose |
|-----------|------|----------|---------|
| `@SubjectId` | UUID | Yes | Aggregate key — determines Merkle tree, sequence counter, audit trail |
| `@ActorId` | String | No | Override for delegation — defaults to `CurrentPrincipal.actorId()` |
| `@TenancyId` | String | No | Override for non-HTTP contexts — defaults to `CurrentPrincipal.tenancyId()` |
| `@DecisionContext` | String | No | JSON snapshot for ComplianceSupplement.decisionContext |
| `@ConfidenceScore` | double | No | Confidence value — used by both ComplianceSupplement and @Attested |
| `@Verdict` | AttestationVerdict | No | Dynamic verdict for @Attested (overrides static annotation value) |

`@SubjectId` is required because a wrong subjectId places the entry in the wrong aggregate's Merkle tree permanently — the chain is append-only with no correction mechanism. No UUID fallback.

## Interceptor Behavior

### AuditedInterceptor

Single CDI interceptor (`@Interceptor @Audited @Priority(APPLICATION + 1)`) handling all annotation combinations. Priority is `APPLICATION + 1` so that `ProvenanceCaptureInterceptor` (at `APPLICATION`) runs as the outer interceptor when both are present — provenance context is active when `LedgerAppender.append()` is called, ensuring `ProvenanceCaptureEnricher` attaches the supplement.

#### Path 1 — `@Audited` alone

```
ic.proceed() → success → resolve fields → LedgerAppender.append(AuditRecord, tenancyId) → return result
```

#### Path 2 — `@Audited` + `@ComplianceSupplement`

```
push ComplianceSupplementContext (static attrs + @ConfidenceScore + @DecisionContext values)
  → ic.proceed() → success → LedgerAppender.append(AuditRecord, tenancyId)
    → ComplianceSupplementEnricher attaches JpaComplianceSupplement during save pipeline
  → pop context
→ return result
```

`ComplianceSupplementEnricher` is a new `LedgerEntryEnricher` at Priority 35 (between ProvenanceCaptureEnricher at 30 and ActorDIDEnricher at 40). It reads from `ComplianceSupplementContext` (ThreadLocal stack, same pattern as `ProvenanceContext`).

#### Path 3 — `@Audited` + `@Attested`

```
ic.proceed() → success → resolve fields → OutcomeRecorder.record(OutcomeRecord, tenancyId) → return result
```

The interceptor switches write path from `LedgerAppender` to `OutcomeRecorder` when `@Attested` is present. Attestor identity comes from `casehub.ledger.outcome.default-attestor-id` config (same as `DefaultOutcomeRecorder`).

Verdict and confidence are resolved from `@Verdict`/`@ConfidenceScore` parameter annotations if present, otherwise from `@Attested` static attribute values.

**Prerequisite SPI change:** `OutcomeRecorder` needs a `record(OutcomeRecord, String tenancyId)` overload. The current `record(OutcomeRecord)` resolves tenancyId internally from `CurrentPrincipal` — this prevents `@TenancyId` override from reaching the write path. The new overload aligns `OutcomeRecorder` with `LedgerAppender.append(AuditRecord, tenancyId)` which already takes an explicit tenancyId parameter. Similarly, `OutcomeRecord` needs an optional `entryType` field so `@Audited.entryType()` is not silently discarded (current `OutcomeRecordSaveService` hardcodes EVENT).

#### Path 4 — `@Audited` + `@ComplianceSupplement` + `@Attested`

Combines Path 2 and Path 3: pushes compliance context, uses `OutcomeRecorder` as write path.

### Failure auditing

When `auditFailures = true`:

```java
try {
    Object result = ic.proceed();
    recordSuccess(ic, result);  // normal audit entry
    return result;
} catch (Exception e) {
    recordFailure(ic, e);       // entry with entryType = COMMAND
    throw e;                    // always re-throw
}
```

Failure entries use `entryType = COMMAND` ("this was attempted") regardless of the annotation's `entryType` value.

**Failure + `@Attested` interaction:** When `auditFailures = true` and `@Attested` is present, failure paths fall back to `LedgerAppender.append()` (audit-only, no attestation). An attestation verdict is meaningless for a failed execution — the method didn't produce a result to attest. Only successful executions on `@Attested` paths create attestations.

### Field resolution

| Field | Resolution order |
|-------|-----------------|
| `subjectId` | `@SubjectId` parameter (required — build-time validated) |
| `actorId` | `@ActorId` parameter → `CurrentPrincipal.actorId()` |
| `actorType` | Defaults to `ActorType.AGENT` (matches `AuditRecord` default) |
| `tenancyId` | `@TenancyId` parameter → `CurrentPrincipal.tenancyId()` |
| `verdict` | `@Verdict` parameter → `@Attested.verdict()` static value (only when `@Attested` present) |
| `confidence` | `@ConfidenceScore` parameter → `@Attested.confidence()` static value (only when `@Attested` present) |

Resolution logic mirrors `ProvenanceCaptureInterceptor.resolveEntityId()` — scan method parameters for the annotation, read the argument value at runtime.

### Annotation resolution

The interceptor reads annotations from the method first, then the declaring class (same as `ProvenanceCaptureInterceptor.resolveAnnotation()`). Method-level overrides type-level defaults.

## Build Extension Validation

`LedgerAnnotationsProcessor` — Jandex-based build-time validation only. No code generation, no synthetic beans.

### Validation rules

| Check | Severity | Message |
|-------|----------|---------|
| `@Audited` method missing `@SubjectId` parameter | ERROR | `@Audited on method 'X' has no @SubjectId parameter` |
| `@SubjectId` parameter is not UUID | ERROR | `@SubjectId parameter on method 'X' must be java.util.UUID` |
| `@Attested` without `@Audited` | ERROR | `@Attested on method 'X' requires @Audited` |
| `@ComplianceSupplement` without `@Audited` | ERROR | `@ComplianceSupplement on method 'X' requires @Audited` |
| `@ConfidenceScore` parameter not double | ERROR | `@ConfidenceScore parameter on method 'X' must be double` |
| `@DecisionContext` parameter not String | ERROR | `@DecisionContext parameter on method 'X' must be String` |
| Multiple `@SubjectId` on same method | ERROR | `Method 'X' has multiple @SubjectId parameters` |
| `@ActorId` parameter not String | ERROR | `@ActorId parameter on method 'X' must be java.lang.String` |
| `@TenancyId` parameter not String | ERROR | `@TenancyId parameter on method 'X' must be java.lang.String` |
| `@Verdict` parameter not AttestationVerdict | ERROR | `@Verdict parameter on method 'X' must be AttestationVerdict` |
| `@Attested` confidence out of range (0.0, 1.0] | ERROR | `@Attested confidence on method 'X' must be in (0.0, 1.0]` |
| `@Attested` with no confidence source | ERROR | `@Attested on method 'X' has no confidence — set attribute or add @ConfidenceScore parameter` |
| `@ComplianceSupplement` with default `auditFailures` | WARN | `EU AI Act Art.12 may require attempt recording for compliance methods` |

## Usage Examples

### Basic audit

```java
@Audited
AnalysisResult analyse(
        @SubjectId UUID caseId,
        String document) {
    // actorId from CurrentPrincipal
    // tenancyId from CurrentPrincipal
    return doAnalysis(document);
}
```

### Compliance-annotated worker

```java
@Audited(entryType = LedgerEntryType.COMMAND, auditFailures = true)
@ComplianceSupplement(
        algorithmRef = "risk-classifier-v2.1",
        contestationUri = "https://example.com/decisions/challenge",
        humanOverrideAvailable = true)
RiskAssessment assessRisk(
        @SubjectId UUID caseId,
        @ActorId String agentId,
        @DecisionContext String contextJson,
        @ConfidenceScore double confidence,
        String document) {
    return classifier.assess(document);
}
```

### Audited with static attestation

```java
@Audited
@Attested(verdict = AttestationVerdict.SOUND, confidence = 0.9,
          capabilityTag = "review")
ReviewResult review(
        @SubjectId UUID caseId,
        @ActorId String reviewerAgentId,
        String submission) {
    return doReview(submission);
}
```

### Audited with dynamic attestation

```java
@Audited
@Attested(capabilityTag = "review")
ReviewResult review(
        @SubjectId UUID caseId,
        @Verdict AttestationVerdict verdict,
        @ConfidenceScore double confidence,
        String submission) {
    // verdict and confidence determined by caller based on review outcome
    return doReview(submission);
}
```

### Non-HTTP context (scheduled job)

```java
@Audited
void reconcile(
        @SubjectId UUID subjectId,
        @ActorId String systemActorId,
        @TenancyId String tenancyId) {
    // No CurrentPrincipal needed — all fields from parameters
    performReconciliation(subjectId);
}
```

## Prerequisite SPI Changes

These changes to existing ledger SPIs are required before the annotation module can be implemented. They are pre-release breaking changes — no backward compatibility concern.

### `OutcomeRecorder.record(OutcomeRecord, String tenancyId)`

New overload. The existing `record(OutcomeRecord)` resolves tenancyId internally from `CurrentPrincipal` — this prevents `@TenancyId` override from reaching the `@Attested` write path. The new overload aligns `OutcomeRecorder` with `LedgerAppender.append(AuditRecord, tenancyId)`. The existing no-tenancyId overload delegates to the new one with `currentPrincipal.tenancyId()`.

### `OutcomeRecord.entryType` field

New optional field (defaults to `LedgerEntryType.EVENT`). Currently `OutcomeRecordSaveService.buildEntry()` hardcodes `entry.entryType = EVENT`. The field ensures `@Audited(entryType = COMMAND)` is not silently discarded on `@Attested` paths.

## Relationship to Existing Ledger Annotations

| Existing | New | Relationship |
|----------|-----|-------------|
| `@ProvenanceCapture` | `@Audited` | Same interceptor pattern. ProvenanceCapture pushes context for enricher. @Audited creates entries. They compose — a method can have both. |
| `@SourceEntityId` | `@SubjectId` | Same parameter annotation pattern. Different semantics — SourceEntityId is informational provenance, SubjectId is structural aggregate routing. |
| `ComplianceSupplement` (api model) | `@ComplianceSupplement` (annotation) | Annotation populates the model. Different packages (`io.casehub.ledger.api.model.supplement` vs `io.casehub.ledger.annotations`). |

## Relationship to Blocks @Attestation

Blocks `@Attestation` (in `casehub-blocks` api) is an orchestration-level annotation that wires `LifecycleAttestationObserver` for pattern execution results — it fires attestation intents via `AttestationIntentWriter`.

Ledger `@Attested` is the recording layer — it writes attestation entries to the ledger directly via `OutcomeRecorder`. Different concerns, different layers. A method could have both: `@Attestation` for orchestration-level lifecycle observation, `@Attested` for direct ledger recording.

## Testing Strategy

### Unit tests (annotations runtime module — pure JUnit 5)
- Annotation presence and attribute reflection tests
- Parameter annotation target/retention validation

### Integration tests (deployment module — @QuarkusTest)
- Build-time validation: missing `@SubjectId` produces compile error
- Build-time validation: `@Attested` without `@Audited` produces compile error
- Build-time validation: wrong parameter types produce compile errors
- Interceptor creates ledger entry on successful method execution
- Interceptor does not create entry on method exception (auditFailures=false)
- Interceptor creates entry on method exception (auditFailures=true)
- `@ComplianceSupplement` attaches supplement with static + dynamic fields
- `@Attested` creates entry + attestation via OutcomeRecorder
- Field resolution: `@SubjectId`, `@ActorId` override, `@TenancyId` override
- Field resolution: actorId fallback to CurrentPrincipal
- Field resolution: tenancyId fallback to CurrentPrincipal
- Composition: `@Audited` + `@ProvenanceCapture` on same method
- Type-level annotation with method-level override

### Example module
- `audited-worker-annotated` — demonstrates `@Audited` + `@ComplianceSupplement` on a worker

## References

- `runtime/src/main/java/.../service/intercept/ProvenanceCapture.java` — CDI interceptor binding pattern
- `runtime/src/main/java/.../service/intercept/ProvenanceCaptureInterceptor.java` — interceptor implementation
- `runtime/src/main/java/.../service/intercept/ProvenanceContext.java` — ThreadLocal context pattern
- `runtime/src/main/java/.../service/intercept/ProvenanceCaptureEnricher.java` — LedgerEntryEnricher pattern
- `api/src/main/java/.../model/AuditRecord.java` — write-path input type
- `api/src/main/java/.../model/OutcomeRecord.java` — combined entry+attestation input
- `api/src/main/java/.../spi/LedgerAppender.java` — blocking write SPI
- `api/src/main/java/.../spi/OutcomeRecorder.java` — combined write SPI
- `api/src/main/java/.../model/supplement/ComplianceSupplement.java` — compliance model class
- `engine/annotations/` — reference implementation for two-layer module structure
- `engine/annotations/deployment/.../EngineAnnotationsProcessor.java` — Jandex build step pattern
- blocks#115 design spec — annotation-driven model architecture
- GE-20260612-17c161 — LedgerProcessor build step enforcement (save pipeline)
- GE-20260531-d2ed26 — LedgerEntryRepository.save() triggers enricher pipeline
