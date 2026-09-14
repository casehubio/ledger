package io.casehub.ledger.rest.dto;

import java.util.Map;
import java.util.OptionalDouble;

public record CapabilityScoreResponse(
        String actorId,
        String capabilityTag,
        OptionalDouble score,
        int decisionCount,
        Map<String, Double> qualityScores) {
}
