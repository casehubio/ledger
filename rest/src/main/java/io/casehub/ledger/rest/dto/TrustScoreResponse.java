package io.casehub.ledger.rest.dto;

import java.util.Map;
import java.util.OptionalDouble;

public record TrustScoreResponse(
        String actorId,
        OptionalDouble globalScore,
        Map<String, Double> capabilityScores,
        Map<String, Double> dimensionScores) {
}
