package io.casehub.ledger.core.trust;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.AttestationRelevanceContext;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EigenTrustEdgeWeights {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(EigenTrustEdgeWeights.class.getName());
    private static final ObjectMapper SNAPSHOT_MAPPER = new ObjectMapper();

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

    public static List<AttestationRelevanceContext> buildRelevanceContexts(
            List<LedgerAttestation> attestations,
            Map<UUID, String> entryActorIndex) {
        return attestations.stream()
                           .map(att -> new AttestationRelevanceContext(
                                   att.id, att.attestorId, att.attestorType, att.attestorRole,
                                   entryActorIndex.get(att.ledgerEntryId), null,
                                   att.capabilityTag, att.trustDimension, att.occurredAt,
                                   parseSnapshot(att)))
                           .toList();
    }

    private static Map<String, Object> parseSnapshot(LedgerAttestation att) {
        if (att.relevanceSnapshot != null && !att.relevanceSnapshot.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = SNAPSHOT_MAPPER.readValue(att.relevanceSnapshot, Map.class);
                return parsed;
            } catch (Exception ex) {
                log.log(java.util.logging.Level.FINE,
                        "Failed to parse relevanceSnapshot for attestation {0}: {1}",
                        new Object[]{att.id, ex.getMessage()});
                return Map.of();
            }
        }
        return Map.of();
    }
}
