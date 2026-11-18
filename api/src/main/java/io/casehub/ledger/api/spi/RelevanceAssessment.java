package io.casehub.ledger.api.spi;

/**
 * The raw relevance weight for one attestation, with an optional diagnostic reason.
 *
 * <p>Weight is in [0.0, 1.0]. After mass-preserving normalisation,
 * the effective weight used in computation may exceed 1.0 for above-mean
 * attestations — that normalisation happens in {@code TrustScoreComputer},
 * not here.
 *
 * <p>The {@code reason} field is for diagnostics and DEBUG logging
 * (e.g. {@code "org-distance:3, weight:0.65"}), not for persistence or
 * compliance reporting.
 */
public record RelevanceAssessment(double weight, String reason) {

    public static final RelevanceAssessment NEUTRAL = new RelevanceAssessment(1.0, null);

    public RelevanceAssessment {
        if (Double.isNaN(weight) || weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("weight must be in [0.0, 1.0], got: " + weight);
        }
    }
}
