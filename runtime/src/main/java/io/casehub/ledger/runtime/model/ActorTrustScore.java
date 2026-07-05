package io.casehub.ledger.runtime.model;

import java.time.Instant;
import java.util.UUID;

import io.casehub.ledger.api.model.ScoreType;
import io.casehub.platform.api.identity.ActorType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Bayesian Beta trust score for a decision-making actor, scoped by score type.
 *
 * <p>
 * One row per {@code (actor_id, capability_key, dimension_key)} triple.
 * Plain {@code @Entity} — queries via {@code @NamedQuery} + EntityManager.
 */
@Entity
@Table(name = "actor_trust_score", uniqueConstraints = @UniqueConstraint(
        name = "uq_actor_trust_score_key",
        columnNames = {"actor_id", "capability_key", "dimension_key"}))
@NamedQuery(
        name = "ActorTrustScore.findAll",
        query = "SELECT s FROM ActorTrustScore s")
@NamedQuery(
        name = "ActorTrustScore.findGlobalByActorId",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey IS NULL AND s.dimensionKey IS NULL")
@NamedQuery(
        name = "ActorTrustScore.findByActorIdAndScoreType",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType")
@NamedQuery(
        name = "ActorTrustScore.findCapabilityByActorIdAndTag",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey = :capabilityKey AND s.dimensionKey IS NULL")
@NamedQuery(
        name = "ActorTrustScore.findDimensionByActorIdAndKey",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey IS NULL AND s.dimensionKey = :dimensionKey")
@NamedQuery(
        name = "ActorTrustScore.findCapabilityDimensionByKeys",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey = :capabilityKey AND s.dimensionKey = :dimensionKey")
@NamedQuery(
        name = "ActorTrustScore.findCapabilityDimensionsByCapability",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId = :actorId AND s.scoreType = :scoreType AND s.capabilityKey = :capabilityKey")
@NamedQuery(
        name = "ActorTrustScore.findAllByLastComputedAtAfter",
        query = "SELECT s FROM ActorTrustScore s WHERE s.lastComputedAt > :since")
@NamedQuery(
        name = "ActorTrustScore.findCapabilityScoresByActorIds",
        query = "SELECT s FROM ActorTrustScore s WHERE s.actorId IN :actorIds AND s.scoreType = :scoreType AND s.capabilityKey = :capabilityKey AND s.dimensionKey IS NULL")
public class ActorTrustScore {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_type", nullable = false)
    public ScoreType scoreType = ScoreType.GLOBAL;

    /** Capability tag for CAPABILITY and CAPABILITY_DIMENSION rows; null for GLOBAL and DIMENSION. */
    @Column(name = "capability_key")
    public String capabilityKey;

    /** Quality dimension name for DIMENSION and CAPABILITY_DIMENSION rows; null for GLOBAL and CAPABILITY. */
    @Column(name = "dimension_key")
    public String dimensionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type")
    public ActorType actorType;

    @Column(name = "trust_score")
    public double trustScore;

    /** Bayesian Beta α parameter. Stored as 0.0 for DIMENSION and CAPABILITY_DIMENSION rows. */
    @Column(name = "alpha_value")
    public double alpha;

    /** Bayesian Beta β parameter. Stored as 0.0 for DIMENSION and CAPABILITY_DIMENSION rows. */
    @Column(name = "beta_value")
    public double beta;

    @Column(name = "decision_count")
    public int decisionCount;

    @Column(name = "overturned_count")
    public int overturnedCount;

    @Column(name = "attestation_positive")
    public int attestationPositive;

    @Column(name = "attestation_negative")
    public int attestationNegative;

    @Column(name = "last_computed_at")
    public Instant lastComputedAt;

    /**
     * EigenTrust global trust share in [0.0, 1.0]; values sum to ≤ 1.0 across all actors.
     * Only meaningful on GLOBAL rows. Zero when EigenTrust is disabled or not yet computed.
     */
    @Column(name = "global_trust_score")
    public double globalTrustScore;
}
