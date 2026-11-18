package io.casehub.ledger.runtime.service.intercept;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryEnricher;
import io.casehub.ledger.jpa.JpaComplianceSupplement;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Priority(35)
public class ComplianceSupplementEnricher implements LedgerEntryEnricher {

    @Inject
    ComplianceSupplementContext context;

    @Override
    public void enrich(final LedgerEntry entry) {
        if (!context.isActive()) {
            return;
        }
        final ComplianceSupplementContext.State state = context.current();
        final JpaComplianceSupplement cs = new JpaComplianceSupplement();
        cs.algorithmRef = emptyToNull(state.algorithmRef());
        cs.contestationUri = emptyToNull(state.contestationUri());
        cs.humanOverrideAvailable = state.humanOverrideAvailable();
        cs.planRef = emptyToNull(state.planRef());
        cs.rationale = emptyToNull(state.rationale());
        cs.confidenceScore = state.confidenceScore();
        cs.decisionContext = state.decisionContext();
        entry.attach(cs);
    }

    @Override
    public String name() {
        return "compliance-supplement";
    }


    private static String emptyToNull(final String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }
}
