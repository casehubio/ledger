package io.casehub.ledger.graphql.dto;

import java.util.Map;
import org.eclipse.microprofile.graphql.Type;

@Type("TrustRoutingProfile")
public record TrustRoutingProfileType(
        String actorId,
        String capabilityTag,
        Double globalScore,
        Double capabilityScore,
        int decisionCount,
        Map<String, Double> qualityDimensions) {
}
