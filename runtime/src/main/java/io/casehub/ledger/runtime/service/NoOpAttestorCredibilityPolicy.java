package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class NoOpAttestorCredibilityPolicy implements AttestorCredibilityPolicy {

    @Override
    public CredibilityAssessment assess(String attestorId) {
        return CredibilityAssessment.NEUTRAL;
    }
}
