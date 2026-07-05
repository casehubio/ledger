package io.casehub.ledger.api.model;

/**
 * Score type discriminator — determines which key columns are non-null in ActorTrustScore.
 */
public enum ScoreType {
    /** Classic cross-decision score. capability_key and dimension_key are null. */
    GLOBAL,
    /** Capability-scoped binary trust. capability_key is set; dimension_key is null. See ADR 0008. */
    CAPABILITY,
    /** Cross-capability quality dimension. capability_key is null; dimension_key is set. See #62. */
    DIMENSION,
    /** Per-capability quality dimension. Both capability_key and dimension_key are set. See #76. */
    CAPABILITY_DIMENSION
}
