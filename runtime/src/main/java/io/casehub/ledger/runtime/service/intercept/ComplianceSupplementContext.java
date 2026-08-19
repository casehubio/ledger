package io.casehub.ledger.runtime.service.intercept;

import java.util.ArrayDeque;
import java.util.Deque;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ComplianceSupplementContext {

    public record State(
            String algorithmRef,
            String contestationUri,
            boolean humanOverrideAvailable,
            String planRef,
            String rationale,
            Double confidenceScore,
            String decisionContext
    ) {}

    private static final ThreadLocal<Deque<State>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    public void push(final State state) {
        STACK.get().push(state);
    }

    public void pop() {
        final Deque<State> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public boolean isActive() {
        return !STACK.get().isEmpty();
    }

    public State current() {
        return STACK.get().peek();
    }
}
