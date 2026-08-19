# Annotation Capability Matrix

Annotations are the **recommended write-path approach** for recording audit entries,
compliance metadata, and attestations. They are not an alternative programming model —
they are best practice. The programmatic API (`LedgerAppender`, `OutcomeRecorder`,
manual entity construction) remains available as an escape hatch for edge cases
(batch jobs with dynamic audit logic, custom entry construction).

Read-path and infrastructure examples (Merkle verification, trust score routing,
signing adapters, OTel tracing) stay programmatic — annotations don't apply to those.

## Examples

| Example | Domain | Status | Annotations Demonstrated |
|---------|--------|--------|--------------------------|
| **order-processing** | E-commerce — order lifecycle | Upgrade from programmatic | `@Audited`, `@SubjectId`, `@ActorId`, `@Audited(entryType)`, `@Audited(actorRole)` |
| **art22-decision-snapshot** | Regulatory — GDPR Art.22 | Upgrade from programmatic | `@Audited`, `@ComplianceSupplement`, `@DecisionContext`, `@ConfidenceScore`, `@Audited(auditFailures)` |
| **eigentrust-mesh** | Multi-agent — trust network | Upgrade from programmatic | `@Audited`, `@Attested` (static), `@Verdict`, `@ConfidenceScore` (dynamic) |
| **prov-dm-export** | Data lineage — W3C PROV-DM | Upgrade from programmatic | `@Audited` + `@ProvenanceCapture` composition |
| **privacy-pseudonymisation** | GDPR — data protection | Upgrade from programmatic | `@Audited`, `@ComplianceSupplement` |

### Examples that stay programmatic (annotations not applicable)

| Example | Domain | Why programmatic |
|---------|--------|------------------|
| merkle-verification | Integrity proofs | Read-path: verification service queries |
| trust-score-routing | Agent routing | CDI event observation (`@Observes`) |
| otel-trace-wiring | Observability | Automatic enrichment (traceId), no write annotation |
| art12-compliance | EU AI Act Art.12 | Retention/reconstructability — read-path + scheduled jobs |
| vault-transit-signing | Security | AgentSigner SPI infrastructure |
| aws-kms-signing | Security | AgentSigner SPI infrastructure |
| gcp-kms-signing | Security | AgentSigner SPI infrastructure |
| azure-keyvault-signing | Security | AgentSigner SPI infrastructure |

## Capability → Example Matrix

| Capability | Annotation | order-processing | art22-decision | eigentrust-mesh | prov-dm-export | privacy-pseudonymisation | Deployment Tests |
|-----------|------------|:----------------:|:--------------:|:---------------:|:--------------:|:------------------------:|:----------------:|
| Basic audit entry | `@Audited` | ✓ | ✓ | ✓ | ✓ | ✓ | AuditedInterceptorTest |
| Command entry type | `@Audited(entryType = COMMAND)` | ✓ | ✓ | — | — | — | AuditedInterceptorTest |
| Actor role | `@Audited(actorRole)` | ✓ | — | — | — | — | AuditedInterceptorTest |
| Failure auditing | `@Audited(auditFailures = true)` | — | ✓ | — | — | — | AuditedInterceptorTest |
| Aggregate key | `@SubjectId` (required) | ✓ | ✓ | ✓ | ✓ | ✓ | AuditedInterceptorTest, ValidationErrorTest |
| Delegation override | `@ActorId` | ✓ | ✓ | ✓ | — | — | AuditedInterceptorTest |
| Non-HTTP tenancy | `@TenancyId` | — | — | — | — | — | AuditedInterceptorTest |
| Compliance metadata | `@ComplianceSupplement(algorithmRef, ...)` | — | ✓ | — | — | ✓ | ComplianceSupplementTest |
| Decision context | `@DecisionContext` | — | ✓ | — | — | — | ComplianceSupplementTest |
| Confidence score | `@ConfidenceScore` | — | ✓ | ✓ | — | — | ComplianceSupplementTest, AttestedTest |
| Static attestation | `@Attested(verdict, confidence, capabilityTag)` | — | — | ✓ | — | — | AttestedTest |
| Dynamic verdict | `@Verdict` | — | — | ✓ | — | — | AttestedTest |
| Interceptor composition | `@Audited` + `@ProvenanceCapture` | — | — | — | ✓ | — | — |
| Build-time validation | `@SubjectId` required, type checks, `@Attested` requires `@Audited` | — | — | — | — | — | ValidationErrorTest |

## Coverage Summary

| Category | Total | In Examples | In Deployment Tests Only |
|----------|-------|-------------|--------------------------|
| Write-path annotations | 7 | 7 | — |
| Parameter annotations | 5 | 4 | 1 (`@TenancyId`) |
| Composition | 1 | 1 | — |
| Build pipeline | 1 | — | 1 (validation) |
| **Total** | **14** | **12** | **2** |

### Test-only capabilities (no example coverage)

| Capability | Why test-only | Recommendation |
|-----------|---------------|----------------|
| `@TenancyId` | Non-HTTP context (scheduled jobs) — none of the existing examples use scheduled jobs | Add a scheduled-job method to order-processing or create a minimal standalone test |
| Build-time validation | Negative tests (invalid annotation usage) — not demonstrable in a runnable example | Deployment tests only — this is expected |

## How to Run

```bash
# All upgraded examples (after annotation migration)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl \
  examples/order-processing,\
  examples/art22-decision-snapshot,\
  examples/eigentrust-mesh,\
  examples/prov-dm-export,\
  examples/privacy-pseudonymisation

# Deployment tests (build-time validation, interceptor, enricher, attestation)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -f annotations/pom.xml

# Everything (annotations module + all upgraded examples)
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -f annotations/pom.xml && \
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl \
  examples/order-processing,\
  examples/art22-decision-snapshot,\
  examples/eigentrust-mesh,\
  examples/prov-dm-export,\
  examples/privacy-pseudonymisation
```
