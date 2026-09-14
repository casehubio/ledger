package io.casehub.ledger.rest.dto;

public record CreateAttestationRequest(
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
