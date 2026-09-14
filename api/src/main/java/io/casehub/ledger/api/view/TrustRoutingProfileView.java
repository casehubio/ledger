package io.casehub.ledger.api.view;

import java.util.Map;
import java.util.OptionalDouble;

public record TrustRoutingProfileView(
        String actorId,
        String capabilityTag,
        OptionalDouble globalScore,
        OptionalDouble capabilityScore,
        int decisionCount,
        Map<String, Double> qualityScores) {
}
