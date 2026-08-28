package io.casehub.ledger.runtime.model;

import java.time.Instant;
import java.util.UUID;

import io.casehub.ledger.api.model.ScoreType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

@Entity
@Table(name = "trust_score_snapshot")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorGlobal",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.scoreType = io.casehub.ledger.api.model.ScoreType.GLOBAL"
                + " ORDER BY s.occurredAt DESC")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorAndCapability",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.scoreType = io.casehub.ledger.api.model.ScoreType.CAPABILITY"
                + " AND s.capabilityTag = :capabilityTag ORDER BY s.occurredAt DESC")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorAndDimension",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.scoreType = io.casehub.ledger.api.model.ScoreType.DIMENSION"
                + " AND s.dimensionKey = :dimensionKey ORDER BY s.occurredAt DESC")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorAndTimeRange",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.occurredAt >= :from AND s.occurredAt <= :to"
                + " ORDER BY s.occurredAt DESC")
@NamedQuery(
        name = "TrustScoreSnapshot.deleteOlderThan",
        query = "DELETE FROM TrustScoreSnapshot s WHERE s.occurredAt < :cutoff")
public class TrustScoreSnapshot {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_type", nullable = false)
    public ScoreType scoreType;

    @Column(name = "capability_tag")
    public String capabilityTag;

    @Column(name = "dimension_key")
    public String dimensionKey;

    @Column(name = "score", nullable = false)
    public double score;

    @Column(name = "previous_score", nullable = false)
    public double previousScore;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    protected TrustScoreSnapshot() {}

    public TrustScoreSnapshot(final String actorId, final ScoreType scoreType,
            final String capabilityTag, final String dimensionKey,
            final double score, final double previousScore, final Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.scoreType = scoreType;
        this.capabilityTag = capabilityTag;
        this.dimensionKey = dimensionKey;
        this.score = score;
        this.previousScore = previousScore;
        this.occurredAt = occurredAt;
    }
}
