package io.casehub.ledger.graphql.dto;

import java.util.Map;
import org.eclipse.microprofile.graphql.Type;

@Type("TrustCapabilityScore")
public record TrustCapabilityScoreType(
        String actorId,
        String capabilityTag,
        Double score,
        int decisionCount,
        Map<String, Double> qualityScores) {
}
