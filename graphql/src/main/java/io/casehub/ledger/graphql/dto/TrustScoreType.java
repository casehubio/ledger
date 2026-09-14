package io.casehub.ledger.graphql.dto;

import java.util.Map;
import org.eclipse.microprofile.graphql.Type;

@Type("TrustScore")
public record TrustScoreType(
        String actorId,
        Double globalScore,
        Map<String, Double> capabilityScores,
        Map<String, Double> dimensionScores) {
}
