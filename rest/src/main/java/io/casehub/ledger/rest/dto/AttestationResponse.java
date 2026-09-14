package io.casehub.ledger.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record AttestationResponse(
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
}
