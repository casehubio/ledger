package io.casehub.ledger.service;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.TrustScoreJob;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @QuarkusTest} verifying that {@link TrustScoreJob#runComputation()} exercises
 * the EigenTrust pass with real CDI wiring, real {@code RelevanceComposer} discovery,
 * and actual attestation data — not just the no-op path.
 *
 * <p>Refs #253.
 */
@QuarkusTest
@TestProfile(TrustScoreJobEigenTrustIT.EigenTrustJobProfile.class)
class TrustScoreJobEigenTrustIT {

    public static class EigenTrustJobProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "casehub.ledger.trust-score.enabled", "true",
                    "casehub.ledger.trust-score.eigentrust.enabled", "true",
                    "casehub.ledger.trust-score.eigentrust.alpha", "0.5",
                    "casehub.ledger.hash-chain.enabled", "false",
                    "quarkus.scheduler.enabled", "false");
        }

        @Override
        public String getConfigProfile() {
            return "eigentrust-job-test";
        }
    }

    @Inject TrustScoreJob job;
    @Inject ActorTrustScoreRepository trustRepo;
    @Inject LedgerEntryRepository repo;
    @Inject EntityManager em;

    @Test
    @Transactional
    void runComputation_withAttestations_producesNonTrivialGlobalScore() {
        final String actorId = "eigentrust-actor-" + UUID.randomUUID();
        final Instant now = Instant.now();

        LedgerTestFixtures.seedDecision(actorId, now, AttestationVerdict.SOUND, repo, em);
        LedgerTestFixtures.seedDecision(actorId, now.minusSeconds(120), AttestationVerdict.SOUND, repo, em);
        LedgerTestFixtures.seedDecision(actorId, now.minusSeconds(240), AttestationVerdict.FLAGGED, repo, em);

        job.runComputation();

        final var score = trustRepo.findByActorId(actorId);
        assertThat(score).isPresent();
        assertThat(score.get().trustScore).isGreaterThan(0.0);
    }

    @Test
    @Transactional
    void runComputation_eigenTrustPassUpdatesGlobalScore() {
        final String actorId = "eigentrust-global-" + UUID.randomUUID();
        final Instant now = Instant.now();

        for (int i = 0; i < 5; i++) {
            LedgerTestFixtures.seedDecision(actorId, now.minusSeconds(i * 60L),
                    AttestationVerdict.SOUND, repo, em);
        }

        job.runComputation();

        final var globalScores = trustRepo.findByActorIdAndScoreType(actorId, ScoreType.GLOBAL);
        assertThat(globalScores).isNotEmpty();
        assertThat(globalScores.get(0).trustScore).isGreaterThan(0.0);
    }
}
