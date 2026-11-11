package io.casehub.ledger.api.view;

import java.util.UUID;

public record CreateAttestationRequest(
        UUID entryId,
        String attestorId,
        String attestorType,
        String attestorRole,
        String verdict,
        String evidence,
        double confidence,
        String capabilityTag,
        String trustDimension,
        Double dimensionScore) {
}
