package io.casehub.ledger.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.interceptor.InterceptorBinding;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntryType;

class AnnotationDefinitionTest {

    @Test
    void auditedIsInterceptorBinding() {
        assertThat(Audited.class.isAnnotationPresent(InterceptorBinding.class)).isTrue();
    }

    @Test
    void auditedTargetsMethodAndType() {
        Target target = Audited.class.getAnnotation(Target.class);
        assertThat(target.value()).containsExactlyInAnyOrder(ElementType.METHOD, ElementType.TYPE);
    }

    @Test
    void auditedIsRuntimeRetained() {
        assertThat(Audited.class.getAnnotation(Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    void auditedDefaultEntryTypeIsEvent() throws NoSuchMethodException {
        LedgerEntryType defaultValue = (LedgerEntryType)
                Audited.class.getMethod("entryType").getDefaultValue();
        assertThat(defaultValue).isEqualTo(LedgerEntryType.EVENT);
    }

    @Test
    void auditedDefaultAuditFailuresIsFalse() throws NoSuchMethodException {
        boolean defaultValue = (boolean)
                Audited.class.getMethod("auditFailures").getDefaultValue();
        assertThat(defaultValue).isFalse();
    }

    @Test
    void attestedRequiresCapabilityTag() throws NoSuchMethodException {
        assertThat(Attested.class.getMethod("capabilityTag").getDefaultValue()).isNull();
    }

    @Test
    void attestedConfidenceDefaultIsSentinel() throws NoSuchMethodException {
        double defaultValue = (double) Attested.class.getMethod("confidence").getDefaultValue();
        assertThat(defaultValue).isEqualTo(-1.0);
    }

    @Test
    void attestedDefaultVerdictIsSound() throws NoSuchMethodException {
        AttestationVerdict defaultValue = (AttestationVerdict)
                Attested.class.getMethod("verdict").getDefaultValue();
        assertThat(defaultValue).isEqualTo(AttestationVerdict.SOUND);
    }

    @Test
    void subjectIdTargetsParameter() {
        Target target = SubjectId.class.getAnnotation(Target.class);
        assertThat(target.value()).containsExactly(ElementType.PARAMETER);
    }

    @Test
    void actorIdTargetsParameter() {
        Target target = ActorId.class.getAnnotation(Target.class);
        assertThat(target.value()).containsExactly(ElementType.PARAMETER);
    }

    @Test
    void tenancyIdTargetsParameter() {
        Target target = TenancyId.class.getAnnotation(Target.class);
        assertThat(target.value()).containsExactly(ElementType.PARAMETER);
    }

    @Test
    void decisionContextTargetsParameter() {
        Target target = DecisionContext.class.getAnnotation(Target.class);
        assertThat(target.value()).containsExactly(ElementType.PARAMETER);
    }

    @Test
    void confidenceScoreTargetsParameter() {
        Target target = ConfidenceScore.class.getAnnotation(Target.class);
        assertThat(target.value()).containsExactly(ElementType.PARAMETER);
    }

    @Test
    void verdictTargetsParameter() {
        Target target = Verdict.class.getAnnotation(Target.class);
        assertThat(target.value()).containsExactly(ElementType.PARAMETER);
    }

    @Test
    void complianceSupplementIsInterceptorBinding() {
        assertThat(ComplianceSupplement.class.isAnnotationPresent(InterceptorBinding.class)).isTrue();
    }

    @Test
    void attestedIsNotInterceptorBinding() {
        assertThat(Attested.class.isAnnotationPresent(InterceptorBinding.class)).isFalse();
    }

    @Test
    void allParameterAnnotationsAreRuntimeRetained() {
        for (Class<?> ann : new Class<?>[] {
                SubjectId.class, ActorId.class, TenancyId.class,
                DecisionContext.class, ConfidenceScore.class, Verdict.class}) {
            assertThat(ann.getAnnotation(Retention.class).value())
                    .as(ann.getSimpleName())
                    .isEqualTo(RetentionPolicy.RUNTIME);
        }
    }
}
