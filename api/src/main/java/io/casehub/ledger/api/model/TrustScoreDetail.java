package io.casehub.ledger.api.model;

/**
 * Derivation metadata for a trust score. Consumers that want to reason about how a score
 * was computed can opt in via {@link io.casehub.ledger.api.spi.TrustScoreSource#globalScoreDetail}
 * without changing the primary {@code OptionalDouble} API.
 *
 * @param score                   the computed trust score
 * @param algorithm               which algorithm produced this score
 * @param directAttestationCount  number of direct attestations used; -1 if unknown
 * @param dataSufficiency         0.0 = no direct data, 1.0 = fully sufficient
 * @param mixingWeight            hybrid mixing weight; null if not HYBRID
 */
public record TrustScoreDetail(
        double score,
        TrustAlgorithm algorithm,
        int directAttestationCount,
        double dataSufficiency,
        Double mixingWeight) {
}
