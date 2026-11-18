package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;

/**
 * Write-time enricher for attestations — the sibling of
 * {@code LedgerEntryEnricher} for entries.
 *
 * <p>Discovered via {@code Instance<LedgerAttestationEnricher>} (Quarkus) or
 * {@code List<LedgerAttestationEnricher>} (Spring). All three repositories
 * (JPA, Spring JPA, in-memory) invoke the pipeline after entry lookup and
 * tenant validation, before tokenisation and persist.
 *
 * <p>Enrichers populate the {@code relevanceSnapshot} JSON on the attestation.
 * Each enricher writes its own keys. The repository clears
 * {@code relevanceSnapshot} to null before running the pipeline, so
 * caller-supplied values are discarded — the snapshot is system-derived only.
 *
 * <p>Enrichers receive the raw (pre-tokenised) attestor identity. This is
 * critical: tokenised IDs are opaque and would return -1 from
 * {@code OrgAwareDispatch.orgDistance()}.
 *
 * <p>Failures are non-fatal: a failed enricher leaves its snapshot keys absent
 * and logs a warning. The attestation persists with whatever keys succeeded.
 */
public interface LedgerAttestationEnricher {

    void enrich(LedgerAttestation attestation, LedgerEntry entry);

    default int priority() {
        return Integer.MAX_VALUE;
    }

    /** Short identifier for diagnostics. */
    String name();
}
