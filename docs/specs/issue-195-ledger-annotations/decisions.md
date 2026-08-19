# Decisions — casehub-ledger-annotations (#195)

## D1: Drop langchain4j-agentic dependency

**Choice:** No LC4j dependency — ledger annotations are pure governance annotations
**Alternatives:**
- Keep langchain4j-agentic as a direct dependency — enables future composition with @Agent methods
- Optional/provided scope — available when LC4j is on classpath, not required
**Rationale:** @Audited, @ComplianceSupplement, and @Attested compose onto any CDI method. They don't reference LC4j types. Cross-module composition with LC4j happens in the blocks build extension, not via a direct ledger dependency.
**Trade-offs:** If a future ledger annotation needs LC4j types, the dep must be added then.
**Sources:** blocks#115 epic spec — Layer 2 annotations; engine-annotations/runtime/pom.xml (depends on engine-api, not LC4j)
**Exploration:** quick
**Status:** captured

## D2: CDI interceptor mechanism for @Audited

**Choice:** CDI `@InterceptorBinding` + `@AroundInvoke` interceptor that calls `LedgerAppender.append()`
**Alternatives:**
- Build-time synthetic bean generation (Jandex + Gizmo) — generates wiring at build time like engine-annotations
- Hybrid — CDI interceptor for runtime, build extension for validation only
**Rationale:** `@ProvenanceCapture` already proves this pattern in the ledger codebase. The interceptor calls `LedgerAppender.append()` after successful method execution. The build extension handles validation only (checking `@ComplianceSupplement` field validity, tenancy warnings). Ledger annotations don't need to generate new types like engine's CaseDefinition — they just wire calls to existing SPIs.
**Trade-offs:** Less build-time wiring than engine-annotations. The "generates ledger entry wiring" language in the issue is satisfied by the interceptor binding + enricher pattern rather than Gizmo class generation.
**Sources:** ProvenanceCapture.java, ProvenanceCaptureInterceptor.java, DefaultLedgerAppender.java, GE-20260612-17c161, GE-20260531-d2ed26
**Exploration:** quick
**Status:** captured

## D3: Field resolution — @SubjectId required, actorId ambient

**Choice:** `@SubjectId` parameter annotation required (no fallback). `actorId` defaults from `CurrentPrincipal.actorId()` with optional `@ActorId` parameter override for delegation scenarios.
**Alternatives:**
- Both as parameter annotations with UUID fallback — dangerous for subjectId (wrong Merkle tree, permanent)
- ThreadLocal context — caller pushes actorId/subjectId into AuditContext before calling
- CDI injection only — inject CurrentPrincipal for both
**Rationale:** `subjectId` is a domain aggregate key — it determines which Merkle tree, sequence counter, and audit trail the entry joins. A wrong value is permanently bound to the wrong aggregate (append-only chain). This is categorically different from `@SourceEntityId` where a wrong value means slightly wrong provenance metadata. `actorId` is the authenticated principal — ambient context by nature, same category as tenancyId (D8). Optional `@ActorId` override supports delegation where the acting agent differs from the authenticated principal.
**Trade-offs:** `@SubjectId` must always be a method parameter. Methods without a natural subjectId argument must add one or use `LedgerAppender` directly. `@ActorId` is optional — most methods don't need it.
**Sources:** ProvenanceCaptureInterceptor.java (contrast with SourceEntityId — different consequence for wrong value), AuditRecord.java (subjectId required non-null)
**Exploration:** quick
**Status:** revised (R1-03, R1-09 — UUID fallback danger for structural fields)

## D4: Static + dynamic split for @ComplianceSupplement

**Choice:** Static fields (algorithmRef, contestationUri, humanOverrideAvailable) as annotation attributes. Dynamic fields via explicit parameter annotations: `@DecisionContext` for decisionContext JSON, `@ConfidenceScore` for confidenceScore.
**Alternatives:**
- All annotation attributes — can't express dynamic values computed at runtime
- Return value naming convention — fragile, not discoverable, fails silently
- Supplier method — `@Customize`-style static method that receives ComplianceSupplement.Builder
**Rationale:** Most compliance fields are deployment-time constants (which model, where to contest). confidenceScore and decisionContext are computed per invocation. Using explicit parameter annotations (`@ConfidenceScore`, `@DecisionContext`) rather than naming conventions is type-safe, discoverable, and fails at compile time when missing — critical for GDPR Art.22 compliance where incomplete records are unacceptable.
**Trade-offs:** Callers must add `@ConfidenceScore` and `@DecisionContext` parameters to their method signatures when using `@ComplianceSupplement`. The build extension validates that `@ComplianceSupplement` methods have the required parameter annotations.
**Sources:** ComplianceSupplement.java (9 fields), blocks#115 design spec
**Exploration:** quick
**Status:** revised (R1-04 — naming convention is fragile for compliance-critical annotations)

## D5: @Attested composes with @Audited

**Choice:** `@Attested` goes alongside `@Audited` on the same method. The interceptor creates both a ledger entry AND an attestation in one TX via `OutcomeRecorder`. `@Attested` attributes: `verdict` (required), `confidence` (required), `capabilityTag` (required — follows `OutcomeRecord` which rejects null capabilityTag).
**Alternatives:**
- Standalone annotation — `@Attested` works independently, calls `OutcomeRecorder.addAttestation()` on an existing entry
- Meta-attribute on `@Audited` — `@Audited(attested=true, verdict=SOUND)`, simpler but less composable
**Rationale:** `OutcomeRecorder.record()` already creates entry + attestation in one TX. When both `@Audited` and `@Attested` are present, the interceptor switches from `LedgerAppender` to `OutcomeRecorder` as the write path. When only `@Audited` is present, it uses `LedgerAppender`. Attestor identity comes from `casehub.ledger.outcome.default-attestor-id` config (matches `DefaultOutcomeRecorder` pattern). `capabilityTag` is required on `@Attested` because `OutcomeRecord` rejects null and GLOBAL-scoped attestations don't reach `TrustScoreCache`.
**Trade-offs:** When `@Attested` is present without `@Audited`, it's a validation error (build extension catches this). `@Attested` is not independently useful — it modifies `@Audited`'s behavior. Blocks `@Attestation` is a different concern: orchestration-level lifecycle observation (fires attestation intents via `AttestationIntentWriter`). Ledger `@Attested` is the recording layer — writing attestation entries to the ledger directly.
**Depends on:** D2 (CDI interceptor mechanism)
**Sources:** OutcomeRecorder.java, OutcomeRecord.java, DefaultOutcomeRecorder.java, blocks#115 (@Attestation in blocks-annotations)
**Exploration:** quick
**Status:** captured (enriched with attestor resolution per R1-05)

## D6: Two-layer module structure

**Choice:** `annotations/pom.xml` (aggregator) → `annotations/runtime/` (annotations, interceptors, context) → `annotations/deployment/` (Jandex validation)
**Alternatives:**
- Single module — one annotations/ module with both runtime and build step
- Flat in existing modules — annotations in api/, interceptors in runtime/, validation in deployment/
**Rationale:** Matches engine-annotations exactly. Follows the epic's "one *-annotations module per repo" directive. The Quarkus extension pattern requires separate runtime and deployment modules for build-time vs runtime classpath separation.
**Trade-offs:** Three POMs for a relatively small feature. But this is the platform standard.
**Sources:** engine/annotations/pom.xml, engine/annotations/runtime/pom.xml, blocks#115 epic spec
**Exploration:** quick
**Status:** captured

## D7: Audit after success by default, opt-in failure auditing

**Choice:** `@Audited(auditFailures = false)` by default — entry created after `ic.proceed()` returns normally. When `@ComplianceSupplement` is co-present, the build extension warns if `auditFailures` is not explicitly set (since compliance-annotated methods are high-risk automated decisions where EU AI Act Art.12 likely requires attempt recording).
**Alternatives:**
- Always audit (success + failure) — too noisy for non-compliance operational methods
- No failure auditing at all — creates survivorship bias in compliance audit trails
**Rationale:** Plain `@Audited` is operational audit — noise from retries and transient failures is unhelpful. But `@ComplianceSupplement`-annotated methods are compliance-critical automated decisions where EU AI Act Art.12 "automatic event recording" plausibly includes failed attempts. The build extension warning surfaces this without forcing the choice.
**Trade-offs:** When `auditFailures = true`, the interceptor records in a finally block with `entryType = COMMAND` (see D9) for failures. This requires the interceptor to handle exceptions without swallowing them.
**Depends on:** D9 (entryType attribute)
**Sources:** LedgerEntryType (COMMAND | EVENT), EU AI Act Art.12, GDPR Art.22
**Exploration:** quick
**Status:** revised (R1-06 — EU AI Act Art.12 requires attempt recording for compliance methods)

## D8: Tenancy resolution with fallback

**Choice:** `@TenancyId` parameter annotation if present, else `CurrentPrincipal.tenancyId()`. Zero friction for HTTP-context callers (no `@TenancyId` needed), full support for non-HTTP callers (scheduled jobs, async workers — add `@TenancyId` parameter).
**Alternatives:**
- CurrentPrincipal only — breaks in scheduled jobs and async contexts
- `@TenancyId` parameter only — adds friction for the common HTTP case
**Rationale:** An interceptor is transparent — it fires on every invocation. If the annotated method is later called from a scheduled job, CurrentPrincipal-only silently fails. The `@TenancyId`-if-present fallback handles both contexts. This differs from `DefaultOutcomeRecorder` which is an explicit facade call (the developer sees the constraint).
**Trade-offs:** Slightly more complex resolution logic in the interceptor. But the alternative (runtime failure in async contexts) is worse.
**Depends on:** D2 (CDI interceptor mechanism)
**Sources:** DefaultOutcomeRecorder.java, ProvenanceCaptureInterceptor.java
**Exploration:** quick
**Status:** revised (R1-07 — interceptors are transparent, must work in all calling contexts)

## D9: Entry type attribute on @Audited

**Choice:** `@Audited(entryType = LedgerEntryType.EVENT)` attribute, defaulting to EVENT. COMMAND available for methods that change state.
**Alternatives:**
- No attribute — always EVENT
- Auto-detect from method signature (void = COMMAND, non-void = EVENT) — too magical
**Rationale:** EVENT ("this happened") is the right default for audit records. COMMAND ("this was requested") is semantically correct for state-changing operations. Making it an attribute lets developers express the distinction without auto-detection magic.
**Trade-offs:** Most users will never change the default. The attribute exists for semantic correctness in compliance reports.
**Depends on:** D2 (CDI interceptor mechanism)
**Sources:** LedgerEntryType.java, AuditRecord.java
**Exploration:** quick
**Status:** captured (R1-08 — entry type was an implicit gap)
