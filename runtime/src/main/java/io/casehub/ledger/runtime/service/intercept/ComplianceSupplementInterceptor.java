package io.casehub.ledger.runtime.service.intercept;

import java.lang.reflect.Parameter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import io.casehub.ledger.annotations.ComplianceSupplement;
import io.casehub.ledger.annotations.ConfidenceScore;
import io.casehub.ledger.annotations.DecisionContext;

@Interceptor
@ComplianceSupplement
@Priority(Interceptor.Priority.APPLICATION)
public class ComplianceSupplementInterceptor {

    @Inject
    ComplianceSupplementContext context;

    @AroundInvoke
    public Object capture(final InvocationContext ic) throws Exception {
        final ComplianceSupplement annotation = resolveAnnotation(ic);
        if (annotation != null) {
            pushContext(ic, annotation);
        }
        try {
            return ic.proceed();
        } finally {
            if (annotation != null) {
                context.pop();
            }
        }
    }

    private static ComplianceSupplement resolveAnnotation(final InvocationContext ic) {
        final ComplianceSupplement method = ic.getMethod().getAnnotation(ComplianceSupplement.class);
        if (method != null) return method;
        return ic.getMethod().getDeclaringClass().getAnnotation(ComplianceSupplement.class);
    }

    private void pushContext(final InvocationContext ic, final ComplianceSupplement compliance) {
        final Parameter[] params = ic.getMethod().getParameters();
        final Object[] args = ic.getParameters();
        Double confidenceScore = null;
        String decisionContext = null;
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(ConfidenceScore.class) && confidenceScore == null) {
                confidenceScore = (Double) (double) args[i];
            }
            if (params[i].isAnnotationPresent(DecisionContext.class) && decisionContext == null) {
                decisionContext = (String) args[i];
            }
        }
        context.push(new ComplianceSupplementContext.State(
                compliance.algorithmRef(),
                compliance.contestationUri(),
                compliance.humanOverrideAvailable(),
                compliance.planRef(),
                compliance.rationale(),
                confidenceScore,
                decisionContext));
    }
}
