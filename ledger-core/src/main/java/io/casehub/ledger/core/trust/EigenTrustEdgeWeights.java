package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.LedgerAttestation;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EigenTrustEdgeWeights {

    private EigenTrustEdgeWeights() {}

    public static Map<UUID, Double> compute(List<LedgerAttestation> attestations,
                                             DecayFunction decayFunction,
                                             Map<UUID, Double> relevanceByAttestationId,
                                             Instant now) {
        Map<UUID, Double> weights = new HashMap<>();
        for (LedgerAttestation att : attestations) {
            long ageInDays = att.occurredAt != null
                    ? Math.max(0, Duration.between(att.occurredAt, now).toDays())
                    : 0;
            double decay = decayFunction.weight(ageInDays, att.verdict);
            double confidence = att.confidence;
            double relevance = relevanceByAttestationId.getOrDefault(att.id, 1.0);
            weights.put(att.id, decay * confidence * relevance);
        }
        return weights;
    }
}
