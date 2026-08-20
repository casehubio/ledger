package io.casehub.ledger.runtime.service.intercept;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.annotations.ActorId;
import io.casehub.ledger.annotations.Attested;
import io.casehub.ledger.annotations.Audited;
import io.casehub.ledger.annotations.ConfidenceScore;
import io.casehub.ledger.annotations.SubjectId;
import io.casehub.ledger.annotations.TenancyId;
import io.casehub.ledger.annotations.Verdict;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.UUID;

@Interceptor
@Audited
@Priority(Interceptor.Priority.APPLICATION + 1)
public class AuditedInterceptor {

    @Inject
    LedgerAppender appender;

    @Inject
    OutcomeRecorder outcomeRecorder;

    @Inject
    CurrentPrincipal currentPrincipal;
    @Inject
    ComplianceSupplementContext complianceContext;

    private static final ObjectMapper MAPPER = new ObjectMapper();


    @AroundInvoke
    public Object audit(final InvocationContext ic) throws Exception {
        final Audited                                            audited    = resolveAudited(ic);
        final io.casehub.ledger.annotations.ComplianceSupplement compliance = resolveComplianceSupplement(ic);

        final boolean pushedCompliance = compliance != null && !complianceContext.isActive();
        if (pushedCompliance) {
            pushComplianceContext(ic, compliance);
        }
        try {
            if (audited.auditFailures()) {
                try {
                    final Object result = ic.proceed();
                    recordSuccess(ic, audited, result);
                    return result;
                } catch (final Exception e) {
                    recordFailure(ic, audited);
                    throw e;
                }
            } else {
                final Object result = ic.proceed();
                recordSuccess(ic, audited, result);
                return result;
            }
        } finally {
            if (pushedCompliance) {
                complianceContext.pop();
            }
        }}

    private void recordSuccess(final InvocationContext ic, final Audited audited, final Object result) {
        final UUID subjectId = resolveSubjectId(ic);
        final String actorId = resolveActorIdWithFallback(ic);
        final String tenancyId = resolveTenancyId(ic);
        final String actorRole = audited.actorRole().isEmpty() ? null : audited.actorRole();
        final Map<String, Object> domainData = toDomainData(result);

        final Attested attested = resolveAttested(ic);
        if (attested != null) {
            final AttestationVerdict verdict = resolveVerdict(ic, attested);
            final double confidence = resolveConfidence(ic, attested);

            final OutcomeRecord record = OutcomeRecord.of(actorId, subjectId,
                            attested.capabilityTag(), verdict, confidence)
                    .withEntryType(audited.entryType());
            final OutcomeRecord withRole = actorRole != null ? record.withActorRole(actorRole) : record;
            outcomeRecorder.record(withRole, tenancyId);
        } else {
            final AuditRecord record = new AuditRecord(subjectId, actorId, ActorType.AGENT,
                    actorRole, audited.entryType(), null, null, null, domainData);
            appender.append(record, tenancyId);
        }
    }

    private void recordFailure(final InvocationContext ic, final Audited audited) {
        final UUID subjectId = resolveSubjectId(ic);
        final String actorId = resolveActorIdWithFallback(ic);
        final String tenancyId = resolveTenancyId(ic);
        final String actorRole = audited.actorRole().isEmpty() ? null : audited.actorRole();

        final AuditRecord record = new AuditRecord(subjectId, actorId, ActorType.AGENT,
                actorRole, LedgerEntryType.COMMAND, null, null, null, null);
        appender.append(record, tenancyId);
    }

    static Audited resolveAudited(final InvocationContext ic) {
        final Audited method = ic.getMethod().getAnnotation(Audited.class);
        if (method != null) return method;
        return ic.getMethod().getDeclaringClass().getAnnotation(Audited.class);
    }

    static Attested resolveAttested(final InvocationContext ic) {
        final Attested method = ic.getMethod().getAnnotation(Attested.class);
        if (method != null) return method;
        return ic.getMethod().getDeclaringClass().getAnnotation(Attested.class);
    }

    static io.casehub.ledger.annotations.ComplianceSupplement resolveComplianceSupplement(final InvocationContext ic) {
        final io.casehub.ledger.annotations.ComplianceSupplement method =
                ic.getMethod().getAnnotation(io.casehub.ledger.annotations.ComplianceSupplement.class);
        if (method != null) {return method;}
        return ic.getMethod().getDeclaringClass().getAnnotation(io.casehub.ledger.annotations.ComplianceSupplement.class);
    }

    private void pushComplianceContext(final InvocationContext ic,
                                       final io.casehub.ledger.annotations.ComplianceSupplement compliance) {
        final Parameter[] params          = ic.getMethod().getParameters();
        final Object[]    args            = ic.getParameters();
        Double            confidenceScore = null;
        String            decisionContext = null;
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(io.casehub.ledger.annotations.ConfidenceScore.class) && confidenceScore == null) {
                confidenceScore = (Double) (double) args[i];
            }
            if (params[i].isAnnotationPresent(io.casehub.ledger.annotations.DecisionContext.class) && decisionContext == null) {
                decisionContext = (String) args[i];
            }
        }
        complianceContext.push(new ComplianceSupplementContext.State(
                compliance.algorithmRef(),
                compliance.contestationUri(),
                compliance.humanOverrideAvailable(),
                compliance.planRef(),
                compliance.rationale(),
                confidenceScore,
                decisionContext));
    }


    static UUID resolveSubjectId(final InvocationContext ic) {
        final Parameter[] params = ic.getMethod().getParameters();
        final Object[] args = ic.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(SubjectId.class)) {
                return (UUID) args[i];
            }
        }
        throw new IllegalStateException("@Audited method " + ic.getMethod().getName()
                + " has no @SubjectId parameter");
    }

    private String resolveActorIdWithFallback(final InvocationContext ic) {
        final Parameter[] params = ic.getMethod().getParameters();
        final Object[] args = ic.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(ActorId.class)) {
                return (String) args[i];
            }
        }
        return currentPrincipal.actorId();
    }

    private String resolveTenancyId(final InvocationContext ic) {
        final Parameter[] params = ic.getMethod().getParameters();
        final Object[] args = ic.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(TenancyId.class)) {
                return (String) args[i];
            }
        }
        return currentPrincipal.tenancyId();
    }

    static AttestationVerdict resolveVerdict(final InvocationContext ic, final Attested attested) {
        final Parameter[] params = ic.getMethod().getParameters();
        final Object[] args = ic.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(Verdict.class)) {
                return (AttestationVerdict) args[i];
            }
        }
        return attested.verdict();
    }

    static double resolveConfidence(final InvocationContext ic, final Attested attested) {
        final Parameter[] params = ic.getMethod().getParameters();
        final Object[] args = ic.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(ConfidenceScore.class)) {
                return (double) args[i];
            }
        }
        return attested.confidence();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toDomainData(final Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof String || result instanceof Number || result instanceof Boolean) {
            return null;
        }
        try {
            return MAPPER.convertValue(result, Map.class);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

}
