package io.casehub.ledger.api.view;

import java.util.Map;
import java.util.OptionalDouble;

public record TrustScoreView(
        String actorId,
        OptionalDouble globalScore,
        Map<String, Double> capabilityScores,
        Map<String, Double> dimensionScores) {
}
