package io.casehub.ledger.api.view;

import java.util.Map;
import java.util.OptionalDouble;

public record CapabilityScoreView(
        String actorId,
        String capabilityTag,
        OptionalDouble score,
        int decisionCount,
        Map<String, Double> qualityScores) {
}
