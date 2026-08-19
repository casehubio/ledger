package io.casehub.ledger.annotations.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.logging.Logger;

public class LedgerAnnotationsProcessor {

    private static final Logger LOG = Logger.getLogger(LedgerAnnotationsProcessor.class);

    private static final DotName AUDITED = DotName.createSimple("io.casehub.ledger.annotations.Audited");
    private static final DotName ATTESTED = DotName.createSimple("io.casehub.ledger.annotations.Attested");
    private static final DotName COMPLIANCE = DotName.createSimple("io.casehub.ledger.annotations.ComplianceSupplement");
    private static final DotName SUBJECT_ID = DotName.createSimple("io.casehub.ledger.annotations.SubjectId");
    private static final DotName ACTOR_ID = DotName.createSimple("io.casehub.ledger.annotations.ActorId");
    private static final DotName TENANCY_ID = DotName.createSimple("io.casehub.ledger.annotations.TenancyId");
    private static final DotName CONFIDENCE_SCORE = DotName.createSimple("io.casehub.ledger.annotations.ConfidenceScore");
    private static final DotName DECISION_CONTEXT = DotName.createSimple("io.casehub.ledger.annotations.DecisionContext");
    private static final DotName VERDICT = DotName.createSimple("io.casehub.ledger.annotations.Verdict");
    private static final DotName INTERCEPTOR = DotName.createSimple("jakarta.interceptor.Interceptor");

    @BuildStep
    @Produce(ServiceStartBuildItem.class)
    void validateAnnotations(CombinedIndexBuildItem indexBuildItem) {
        IndexView index = indexBuildItem.getIndex();

        for (AnnotationInstance ann : index.getAnnotations(AUDITED)) {
            if (ann.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.METHOD) {
                MethodInfo method = ann.target().asMethod();
                if (method.declaringClass().hasAnnotation(INTERCEPTOR)) continue;
                validateAuditedMethod(method, ann);
            } else if (ann.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.CLASS) {
                ClassInfo clazz = ann.target().asClass();
                if (clazz.hasAnnotation(INTERCEPTOR)) continue;
                for (MethodInfo method : clazz.methods()) {
                    if (method.isSynthetic() || "<init>".equals(method.name()) || "<clinit>".equals(method.name())) {
                        continue;
                    }
                    if (java.lang.reflect.Modifier.isStatic(method.flags())) {
                        continue;
                    }
                    validateAuditedMethod(method, ann);
                }
            }
        }

        for (AnnotationInstance ann : index.getAnnotations(ATTESTED)) {
            if (ann.target().kind() != org.jboss.jandex.AnnotationTarget.Kind.METHOD) {continue;}
            MethodInfo method = ann.target().asMethod();

            if (!method.hasAnnotation(AUDITED) && !method.declaringClass().hasAnnotation(AUDITED)) {
                throw new IllegalStateException("@Attested on method '"
                                                + method.declaringClass().name() + "." + method.name()
                                                + "' requires @Audited — it cannot be used standalone");
            }
            validateAttestedConfidence(ann, method);
        }

        for (AnnotationInstance ann : index.getAnnotations(COMPLIANCE)) {
            if (ann.target().kind() != org.jboss.jandex.AnnotationTarget.Kind.METHOD) {continue;}
            MethodInfo method = ann.target().asMethod();

            if (!method.hasAnnotation(AUDITED) && !method.declaringClass().hasAnnotation(AUDITED)) {
                throw new IllegalStateException("@ComplianceSupplement on method '"
                                                + method.declaringClass().name() + "." + method.name()
                                                + "' requires @Audited");
            }
        }}

    private void validateAuditedMethod(MethodInfo method, AnnotationInstance auditedAnn) {
        validateSubjectId(method);
        validateParameterTypes(method);
        checkComplianceWarning(method, auditedAnn);
    }

    private void validateSubjectId(MethodInfo method) {
        int count = 0;
        for (MethodParameterInfo param : method.parameters()) {
            if (param.hasAnnotation(SUBJECT_ID)) {
                count++;
                if (!param.type().name().toString().equals("java.util.UUID")) {
                    throw new IllegalStateException("@SubjectId parameter on method '"
                            + method.declaringClass().name() + "." + method.name()
                            + "' must be java.util.UUID — got " + param.type().name());
                }
            }
        }
        if (count == 0) {
            throw new IllegalStateException("@Audited on method '"
                    + method.declaringClass().name() + "." + method.name()
                    + "' has no @SubjectId parameter");
        }
        if (count > 1) {
            throw new IllegalStateException("Method '"
                    + method.declaringClass().name() + "." + method.name()
                    + "' has multiple @SubjectId parameters — exactly one allowed");
        }
    }

    private void validateParameterTypes(MethodInfo method) {
        for (MethodParameterInfo param : method.parameters()) {
            if (param.hasAnnotation(ACTOR_ID) && !param.type().name().toString().equals("java.lang.String")) {
                throw new IllegalStateException("@ActorId parameter on method '"
                        + method.declaringClass().name() + "." + method.name()
                        + "' must be java.lang.String — got " + param.type().name());
            }
            if (param.hasAnnotation(TENANCY_ID) && !param.type().name().toString().equals("java.lang.String")) {
                throw new IllegalStateException("@TenancyId parameter on method '"
                        + method.declaringClass().name() + "." + method.name()
                        + "' must be java.lang.String — got " + param.type().name());
            }
            if (param.hasAnnotation(CONFIDENCE_SCORE) && !param.type().name().toString().equals("double")) {
                throw new IllegalStateException("@ConfidenceScore parameter on method '"
                        + method.declaringClass().name() + "." + method.name()
                        + "' must be double — got " + param.type().name());
            }
            if (param.hasAnnotation(DECISION_CONTEXT) && !param.type().name().toString().equals("java.lang.String")) {
                throw new IllegalStateException("@DecisionContext parameter on method '"
                        + method.declaringClass().name() + "." + method.name()
                        + "' must be String — got " + param.type().name());
            }
            if (param.hasAnnotation(VERDICT)
                    && !param.type().name().toString().equals("io.casehub.ledger.api.model.AttestationVerdict")) {
                throw new IllegalStateException("@Verdict parameter on method '"
                        + method.declaringClass().name() + "." + method.name()
                        + "' must be AttestationVerdict — got " + param.type().name());
            }
        }
    }

    private void checkComplianceWarning(MethodInfo method, AnnotationInstance auditedAnn) {
        if (method.hasAnnotation(COMPLIANCE)) {
            AnnotationValue auditFailures = auditedAnn.value("auditFailures");
            if (auditFailures == null || !auditFailures.asBoolean()) {
                LOG.warnf("@ComplianceSupplement on method '%s.%s' with default auditFailures=false"
                          + " — EU AI Act Art.12 may require attempt recording for compliance methods",
                          method.declaringClass().name(), method.name());
            }
        }
    }

    private void validateAttestedConfidence(AnnotationInstance ann, MethodInfo method) {
        boolean hasConfidenceParam = false;
        for (MethodParameterInfo param : method.parameters()) {
            if (param.hasAnnotation(CONFIDENCE_SCORE)) {
                hasConfidenceParam = true;
                break;
            }
        }
        AnnotationValue confidenceVal = ann.value("confidence");
        if (confidenceVal != null) {
            double confidence = confidenceVal.asDouble();
            if (confidence != -1.0 && (confidence <= 0.0 || confidence > 1.0)) {
                throw new IllegalStateException("@Attested confidence on method '"
                        + method.declaringClass().name() + "." + method.name()
                        + "' must be in (0.0, 1.0] — got " + confidence);
            }
            if (confidence == -1.0 && !hasConfidenceParam) {
                throw new IllegalStateException("@Attested on method '"
                        + method.declaringClass().name() + "." + method.name()
                        + "' has no confidence — set attribute or add @ConfidenceScore parameter");
            }
        } else if (!hasConfidenceParam) {
            throw new IllegalStateException("@Attested on method '"
                    + method.declaringClass().name() + "." + method.name()
                    + "' has no confidence — set attribute or add @ConfidenceScore parameter");
        }
    }
}
