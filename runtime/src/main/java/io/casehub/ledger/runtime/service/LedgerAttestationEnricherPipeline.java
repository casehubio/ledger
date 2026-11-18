package io.casehub.ledger.runtime.service;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerAttestationEnricher;
import io.casehub.ledger.core.enricher.AttestationEnricherPipelineCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class LedgerAttestationEnricherPipeline {

    private final AttestationEnricherPipelineCore core;

    @Inject
    LedgerAttestationEnricherPipeline(@Any Instance<LedgerAttestationEnricher> enrichers) {
        this.core = new AttestationEnricherPipelineCore(enrichers.stream().toList());
    }

    public void enrich(final LedgerAttestation attestation, final LedgerEntry entry) {
        core.enrich(attestation, entry);
    }
}
