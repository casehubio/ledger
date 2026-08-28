package io.casehub.ledger.runtime.repository;

import java.time.Instant;
import java.util.List;

import io.casehub.ledger.runtime.model.TrustScoreSnapshot;

public interface TrustScoreSnapshotRepository {

    void save(TrustScoreSnapshot snapshot);

    List<TrustScoreSnapshot> findGlobalSnapshots(String actorId);

    List<TrustScoreSnapshot> findCapabilitySnapshots(String actorId, String capabilityTag);

    List<TrustScoreSnapshot> findDimensionSnapshots(String actorId, String dimensionKey);

    List<TrustScoreSnapshot> findByActorAndTimeRange(String actorId, Instant from, Instant to);

    int deleteOlderThan(Instant cutoff);
}
