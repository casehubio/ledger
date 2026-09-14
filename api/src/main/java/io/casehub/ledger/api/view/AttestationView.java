package io.casehub.ledger.api.view;

import java.time.Instant;
import java.util.UUID;

public record AttestationView(
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
