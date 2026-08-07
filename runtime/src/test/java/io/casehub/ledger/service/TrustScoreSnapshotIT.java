package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.model.TrustScoreSnapshot;
import io.casehub.ledger.runtime.repository.TrustScoreSnapshotRepository;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TrustScoreSnapshotIT.Profile.class)
class TrustScoreSnapshotIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "trust-score-test";
        }
    }

    @Inject
    TrustScoreJob trustScoreJob;

    @Inject
    LedgerEntryRepository repo;

    @Inject
    TrustScoreSnapshotRepository snapshotRepo;

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void firstComputation_capturesSnapshotWithZeroPreviousScore() {
        final String actorId = "snapshot-first-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now.minus(1, ChronoUnit.DAYS),
                AttestationVerdict.SOUND, repo, em);

        trustScoreJob.runComputation();

        final List<TrustScoreSnapshot> snapshots = snapshotRepo.findGlobalSnapshots(actorId);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).actorId).isEqualTo(actorId);
        assertThat(snapshots.get(0).score).isGreaterThan(0.5);
        assertThat(snapshots.get(0).previousScore).isEqualTo(0.0);
        assertThat(snapshots.get(0).capabilityTag).isNull();
    }

    @Test
    @Transactional
    void secondComputation_capturesPreviousScore() {
        final String actorId = "snapshot-prev-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now.minus(2, ChronoUnit.DAYS),
                AttestationVerdict.SOUND, repo, em);
        trustScoreJob.runComputation();

        final List<TrustScoreSnapshot> first = snapshotRepo.findGlobalSnapshots(actorId);
        assertThat(first).hasSize(1);
        final double firstScore = first.get(0).score;

        LedgerTestFixtures.seedDecision(actorId, now.minus(1, ChronoUnit.DAYS),
                AttestationVerdict.ENDORSED, repo, em);
        trustScoreJob.runComputation();

        final List<TrustScoreSnapshot> all = snapshotRepo.findGlobalSnapshots(actorId);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).occurredAt).isAfterOrEqualTo(all.get(1).occurredAt);
        assertThat(all.get(0).previousScore).isEqualTo(firstScore);
    }

    @Test
    @Transactional
    void capabilitySnapshot_capturedSeparately() {
        final String actorId = "snapshot-cap-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now.minus(1, ChronoUnit.DAYS),
                AttestationVerdict.SOUND, now.minus(1, ChronoUnit.DAYS),
                "code-review", repo, em);

        trustScoreJob.runComputation();

        final List<TrustScoreSnapshot> capSnapshots =
                snapshotRepo.findCapabilitySnapshots(actorId, "code-review");
        assertThat(capSnapshots).isNotEmpty();
        assertThat(capSnapshots.get(0).capabilityTag).isEqualTo("code-review");
        assertThat(capSnapshots.get(0).score).isGreaterThan(0.0);
    }
}
