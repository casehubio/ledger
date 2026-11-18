package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;

import java.util.Map;
import java.util.UUID;

/**
 * Groups per-attestor credibility and per-attestation raw relevance weights
 * for the trust computation pipeline. Prevents positional parameter errors.
 *
 * <p>{@code relevanceByAttestationId} contains <b>raw</b> (un-normalised)
 * weights from the {@code RelevanceComposer}. Normalisation happens per pass
 * inside {@code TrustScoreComputer.compute()}.
 */
public record AttestationWeights(
        Map<String, AttestorCredibilityPolicy.CredibilityAssessment> credibilityByAttestorId,
        Map<UUID, Double> relevanceByAttestationId
) {
    public static final AttestationWeights NEUTRAL = new AttestationWeights(Map.of(), Map.of());
}
