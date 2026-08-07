package io.casehub.ledger.runtime.repository;

import java.util.List;

import io.casehub.ledger.runtime.model.TrustScoreSnapshot;

public interface TrustScoreSnapshotRepository {

    void save(TrustScoreSnapshot snapshot);

    List<TrustScoreSnapshot> findGlobalSnapshots(String actorId);

    List<TrustScoreSnapshot> findCapabilitySnapshots(String actorId, String capabilityTag);
}
