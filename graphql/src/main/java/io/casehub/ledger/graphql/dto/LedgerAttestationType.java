package io.casehub.ledger.graphql.dto;

import io.casehub.ledger.api.model.LedgerAttestation;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Type;

@Type("LedgerAttestation")
public record LedgerAttestationType(
        UUID id,
        UUID ledgerEntryId,
        UUID subjectId,
        String attestorId,
        String attestorType,
        String attestorRole,
        String verdict,
        String evidence,
        double confidence,
        String capabilityTag,
        String trustDimension,
        Double dimensionScore,
        Instant occurredAt) {

    public static LedgerAttestationType from(LedgerAttestation attestation) {
        return new LedgerAttestationType(
                attestation.id,
                attestation.ledgerEntryId,
                attestation.subjectId,
                attestation.attestorId,
                attestation.attestorType != null ? attestation.attestorType.name() : null,
                attestation.attestorRole,
                attestation.verdict != null ? attestation.verdict.name() : null,
                attestation.evidence,
                attestation.confidence,
                attestation.capabilityTag,
                attestation.trustDimension,
                attestation.dimensionScore,
                attestation.occurredAt);
    }
}
