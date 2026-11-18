package io.casehub.ledger.core.enricher;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerAttestationEnricher;

import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs attestation enrichers sorted by priority, with non-fatal error handling.
 * Clears {@code relevanceSnapshot} before running enrichers — caller-supplied
 * values are discarded.
 */
public class AttestationEnricherPipelineCore {

    private static final Logger log = Logger.getLogger(AttestationEnricherPipelineCore.class.getName());

    private final List<LedgerAttestationEnricher> enrichers;

    public AttestationEnricherPipelineCore(List<LedgerAttestationEnricher> enrichers) {
        this.enrichers = enrichers.stream()
                .sorted(Comparator.comparingInt(LedgerAttestationEnricher::priority))
                .toList();
    }

    public void enrich(LedgerAttestation attestation, LedgerEntry entry) {
        attestation.relevanceSnapshot = null;
        for (var enricher : enrichers) {
            try {
                enricher.enrich(attestation, entry);
            } catch (Exception ex) {
                log.log(Level.WARNING, "Attestation enricher {0} failed — attestation will still be saved: {1}",
                        new Object[]{enricher.name(), ex.getMessage()});
            }
        }
    }

    public boolean hasEnrichers() {
        return !enrichers.isEmpty();
    }
}
