package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.spi.AttestationRelevanceContext;
import io.casehub.ledger.api.spi.AttestationRelevanceFactor;
import io.casehub.ledger.api.spi.RelevanceAssessment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates all {@link AttestationRelevanceFactor} beans into a single
 * raw relevance weight per attestation. Computes the product of all factor
 * weights, floored at the configured minimum to prevent compounding from
 * driving weights to near-zero.
 *
 * <p>With no factors, returns 1.0 (NEUTRAL) for all attestations.
 */
public class RelevanceComposer {

    private final List<AttestationRelevanceFactor> factors;
    private final double minWeight;

    public RelevanceComposer(List<AttestationRelevanceFactor> factors, double minWeight) {
        if (minWeight <= 0.0 || minWeight > 1.0) {
            throw new IllegalArgumentException("minWeight must be in (0.0, 1.0], got: " + minWeight);
        }
        this.factors = List.copyOf(factors);
        this.minWeight = minWeight;
    }

    public Map<UUID, Double> assess(List<AttestationRelevanceContext> contexts) {
        if (factors.isEmpty()) {
            Map<UUID, Double> result = new LinkedHashMap<>();
            for (AttestationRelevanceContext ctx : contexts) {
                result.put(ctx.attestationId(), 1.0);
            }
            return result;
        }

        Map<UUID, Double> products = new LinkedHashMap<>();
        for (AttestationRelevanceContext ctx : contexts) {
            products.put(ctx.attestationId(), 1.0);
        }

        for (AttestationRelevanceFactor factor : factors) {
            Map<UUID, RelevanceAssessment> batch = factor.assessBatch(contexts);
            for (var entry : batch.entrySet()) {
                products.merge(entry.getKey(), entry.getValue().weight(),
                               (existing, weight) -> existing * weight);
            }
        }

        Map<UUID, Double> result = new LinkedHashMap<>();
        for (var entry : products.entrySet()) {
            result.put(entry.getKey(), Math.max(minWeight, entry.getValue()));
        }
        return result;
    }
}
