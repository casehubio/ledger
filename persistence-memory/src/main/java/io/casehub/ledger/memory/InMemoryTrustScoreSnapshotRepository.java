package io.casehub.ledger.memory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.ledger.runtime.model.TrustScoreSnapshot;
import io.casehub.ledger.runtime.repository.TrustScoreSnapshotRepository;

@Alternative
@Priority(1)
@ApplicationScoped
public class InMemoryTrustScoreSnapshotRepository implements TrustScoreSnapshotRepository {

    private final CopyOnWriteArrayList<TrustScoreSnapshot> store = new CopyOnWriteArrayList<>();

    @Override
    public void save(final TrustScoreSnapshot snapshot) {
        store.add(snapshot);
    }

    @Override
    public List<TrustScoreSnapshot> findGlobalSnapshots(final String actorId) {
        return store.stream()
                .filter(s -> actorId.equals(s.actorId))
                .filter(s -> s.capabilityTag == null)
                .sorted(Comparator.comparing((TrustScoreSnapshot s) -> s.occurredAt).reversed())
                .toList();
    }

    @Override
    public List<TrustScoreSnapshot> findCapabilitySnapshots(final String actorId,
            final String capabilityTag) {
        return store.stream()
                .filter(s -> actorId.equals(s.actorId))
                .filter(s -> capabilityTag.equals(s.capabilityTag))
                .sorted(Comparator.comparing((TrustScoreSnapshot s) -> s.occurredAt).reversed())
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
