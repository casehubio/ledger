package io.casehub.ledger.runtime.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

@Entity
@Table(name = "trust_score_snapshot")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorGlobal",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.capabilityTag IS NULL ORDER BY s.occurredAt DESC")
@NamedQuery(
        name = "TrustScoreSnapshot.findByActorAndCapability",
        query = "SELECT s FROM TrustScoreSnapshot s WHERE s.actorId = :actorId"
                + " AND s.capabilityTag = :capabilityTag ORDER BY s.occurredAt DESC")
public class TrustScoreSnapshot {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "actor_id", nullable = false)
    public String actorId;

    @Column(name = "capability_tag")
    public String capabilityTag;

    @Column(name = "score", nullable = false)
    public double score;

    @Column(name = "previous_score", nullable = false)
    public double previousScore;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    protected TrustScoreSnapshot() {}

    public TrustScoreSnapshot(final String actorId, final String capabilityTag,
            final double score, final double previousScore, final Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.capabilityTag = capabilityTag;
        this.score = score;
        this.previousScore = previousScore;
        this.occurredAt = occurredAt;
    }
}
