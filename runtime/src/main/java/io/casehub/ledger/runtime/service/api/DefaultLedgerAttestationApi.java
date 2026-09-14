package io.casehub.ledger.runtime.service.api;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.LedgerAttestationApi;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.view.AttestationView;
import io.casehub.ledger.api.view.CreateAttestationRequest;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@DefaultBean
@ApplicationScoped
public class DefaultLedgerAttestationApi implements LedgerAttestationApi {

    @Inject LedgerEntryRepository repository;

    @Override
    public List<AttestationView> listAttestations(final UUID entryId,
                                                    final String tenancyId,
                                                    final String capabilityTag) {
        final String tid = DefaultLedgerEntryApi.defaultTenancyId(tenancyId);
        final List<LedgerAttestation> attestations = capabilityTag != null
                ? repository.findAttestationsByEntryIdAndCapabilityTag(
                        entryId, capabilityTag, tid)
                : repository.findAttestationsByEntryId(entryId, tid);
        return attestations.stream()
                .map(DefaultLedgerAttestationApi::toView).toList();
    }

    @Override
    public AttestationView createAttestation(final CreateAttestationRequest request,
                                              final String tenancyId) {
        final String tid = DefaultLedgerEntryApi.defaultTenancyId(tenancyId);
        final var entry = repository.findEntryById(request.entryId(), tid)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Entry not found: " + request.entryId()));

        final var attestation = new LedgerAttestation();
        attestation.id = UUID.randomUUID();
        attestation.ledgerEntryId = request.entryId();
        attestation.subjectId = entry.subjectId;
        attestation.attestorId = request.attestorId();
        attestation.attestorType = ActorType.valueOf(request.attestorType());
        attestation.attestorRole = request.attestorRole();
        attestation.verdict = AttestationVerdict.valueOf(request.verdict());
        attestation.evidence = request.evidence();
        attestation.confidence = request.confidence();
        attestation.capabilityTag = request.capabilityTag();
        attestation.trustDimension = request.trustDimension();
        attestation.dimensionScore = request.dimensionScore();
        attestation.occurredAt = Instant.now();

        final LedgerAttestation saved = repository.saveAttestation(attestation, tid);
        return toView(saved);
    }

    static AttestationView toView(final LedgerAttestation a) {
        return new AttestationView(
                a.id, a.ledgerEntryId, a.subjectId,
                a.attestorId,
                a.attestorType != null ? a.attestorType.name() : null,
                a.attestorRole,
                a.verdict != null ? a.verdict.name() : null,
                a.evidence, a.confidence, a.capabilityTag,
                a.trustDimension, a.dimensionScore, a.occurredAt);
    }
}
