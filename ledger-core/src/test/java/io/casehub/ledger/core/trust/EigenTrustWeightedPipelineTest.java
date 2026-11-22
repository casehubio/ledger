package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import io.casehub.ledger.api.spi.AttestationRelevanceContext;
import io.casehub.ledger.api.spi.AttestationRelevanceFactor;
import io.casehub.ledger.api.spi.CrossTenantLedgerEntryRepository;
import io.casehub.ledger.api.spi.RelevanceAssessment;
import io.casehub.ledger.api.spi.TrustScoreSnapshotRepository;
import io.casehub.ledger.core.config.TrustScoreProperties;
import io.casehub.ledger.core.event.TrustScoreEventPublisher;
import io.casehub.ledger.core.federation.TrustBootstrapServiceCore;
import io.casehub.ledger.core.federation.TrustBootstrapSource;
import io.casehub.ledger.core.federation.TrustImportService;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the EigenTrust weighted pipeline:
 * TrustScoreComputationService → RelevanceComposer → EigenTrustEdgeWeights → EigenTrustComputer.
 *
 * Verifies that decay, confidence, and relevance factors propagate through
 * the full pipeline and affect the computed global trust scores.
 */
class EigenTrustWeightedPipelineTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    private static LedgerEntry entry(String actorId) {
        LedgerEntry e = new LedgerEntry() {};
        e.id = UUID.randomUUID();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.occurredAt = NOW;
        return e;
    }

    private static LedgerAttestation attestation(UUID ledgerEntryId, String attestorId,
                                                  AttestationVerdict verdict, double confidence,
                                                  Instant occurredAt) {
        LedgerAttestation att = new LedgerAttestation();
        att.id = UUID.randomUUID();
        att.ledgerEntryId = ledgerEntryId;
        att.subjectId = UUID.randomUUID();
        att.attestorId = attestorId;
        att.attestorType = ActorType.AGENT;
        att.verdict = verdict;
        att.confidence = confidence;
        att.occurredAt = occurredAt;
        return att;
    }

    private TrustScoreComputationService buildService(
            CrossTenantLedgerEntryRepository ledgerRepo,
            ActorTrustScoreRepository trustRepo,
            List<AttestationRelevanceFactor> relevanceFactors) {

        var snapshotRepo = mock(TrustScoreSnapshotRepository.class);
        var publisher = mock(TrustScoreEventPublisher.class);
        when(publisher.needsDeltaPayload()).thenReturn(false);

        var props = new TrustScoreProperties(
                false, 90, true, 0.01, "24h",
                new TrustScoreProperties.EigenTrustProperties(true, 0.15, Optional.empty()),
                TrustScoreProperties.ExportProperties.defaults(),
                TrustScoreProperties.BootstrapProperties.defaults(),
                TrustScoreProperties.MaterializationProperties.defaults(),
                TrustScoreProperties.IncrementalProperties.defaults(),
                TrustScoreProperties.SnapshotProperties.defaults(),
                AttestationAggregator.Strategy.WEIGHTED_MAJORITY
        );

        var calculator = new TrustScoreCalculator(
                new ExponentialDecayFunction(90, 1.5),
                new AllAttestationsGlobalStrategy(),
                new NoOpAttestorCredibilityPolicy(),
                new RelevanceComposer(relevanceFactors, 0.5));
        var perActor = new PerActorTrustComputerCore(calculator, trustRepo, snapshotRepo);
        var publisherCore = new TrustScorePublisherCore(publisher, props);
        var bootstrap = new TrustBootstrapServiceCore(
                mock(TrustBootstrapSource.class), mock(TrustImportService.class));
        var eigenTrust = new EigenTrustComputer(0.15);

        return new TrustScoreComputationService(
                ledgerRepo, trustRepo, snapshotRepo, perActor, publisherCore,
                props, bootstrap, eigenTrust,
                new ExponentialDecayFunction(90, 1.5),
                new RelevanceComposer(relevanceFactors, 0.5));
    }

    private Map<String, Double> runAndCapture(
            List<LedgerEntry> entries,
            Map<UUID, List<LedgerAttestation>> attestationsByEntry,
            List<AttestationRelevanceFactor> factors) {

        var ledgerRepo = mock(CrossTenantLedgerEntryRepository.class);
        var trustRepo = mock(ActorTrustScoreRepository.class);

        when(ledgerRepo.findAllEvents()).thenReturn(entries);
        when(ledgerRepo.findAttestationsForEntries(any())).thenReturn(attestationsByEntry);
        when(trustRepo.findAll()).thenReturn(List.of());
        when(trustRepo.findAllDetached()).thenReturn(List.of());

        var service = buildService(ledgerRepo, trustRepo, factors);
        service.runComputation();

        var actorCaptor = ArgumentCaptor.forClass(String.class);
        var scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(trustRepo, atLeastOnce()).updateGlobalTrustScore(
                actorCaptor.capture(), scoreCaptor.capture());

        var actors = actorCaptor.getAllValues();
        var scores = scoreCaptor.getAllValues();
        Map<String, Double> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < actors.size(); i++) {
            result.put(actors.get(i), scores.get(i));
        }
        return result;
    }

    @Test
    void eigenTrustPassProducesGlobalScores() {
        LedgerEntry aliceDecision = entry("alice");
        LedgerAttestation bobAttestation = attestation(
                aliceDecision.id, "bob", AttestationVerdict.SOUND, 1.0, NOW);

        Map<String, Double> scores = runAndCapture(
                List.of(aliceDecision),
                Map.of(aliceDecision.id, List.of(bobAttestation)),
                List.of());

        assertTrue(scores.containsKey("alice"), "alice should have an EigenTrust score");
        assertTrue(scores.containsKey("bob"), "bob should have an EigenTrust score");
        assertTrue(scores.values().stream().allMatch(s -> s > 0.0),
                "all scores should be positive");
    }

    @Test
    void decayReducesOldAttestationImpact() {// charlie attests alice's recent decision and bob's old decision.
// After row normalization, charlie's trust favours alice.
        LedgerEntry aliceDecision = entry("alice");
        LedgerEntry bobDecision   = entry("bob");

        LedgerAttestation charlieAttestsAlice = attestation(
                aliceDecision.id, "charlie", AttestationVerdict.SOUND, 1.0,
                NOW.minus(1, ChronoUnit.DAYS));
        LedgerAttestation charlieAttestsBob = attestation(
                bobDecision.id, "charlie", AttestationVerdict.SOUND, 1.0,
                NOW.minus(1000, ChronoUnit.DAYS));
// alice and bob attest each other so neither is a dangling node
        LedgerAttestation aliceAttestsBob = attestation(
                bobDecision.id, "alice", AttestationVerdict.SOUND, 1.0,
                NOW.minus(1, ChronoUnit.DAYS));
        LedgerAttestation bobAttestsAlice = attestation(
                aliceDecision.id, "bob", AttestationVerdict.SOUND, 1.0,
                NOW.minus(1, ChronoUnit.DAYS));

        Map<String, Double> scores = runAndCapture(
                List.of(aliceDecision, bobDecision),
                Map.of(aliceDecision.id, List.of(charlieAttestsAlice, bobAttestsAlice),
                       bobDecision.id, List.of(charlieAttestsBob, aliceAttestsBob)),
                List.of());

        double aliceScore = scores.get("alice");
        double bobScore   = scores.get("bob");
        assertTrue(aliceScore > bobScore,
                   "alice (attested recently by charlie) should score higher than bob (attested long ago): " +
                   "alice=" + aliceScore + ", bob=" + bobScore);
    }

    @Test
    void confidenceAffectsEigenTrustScores() {// charlie attests alice with high confidence and bob with low confidence.
// After row normalization, charlie's trust favours alice.
        LedgerEntry aliceDecision = entry("alice");
        LedgerEntry bobDecision   = entry("bob");

        LedgerAttestation charlieAttestsAlice = attestation(
                aliceDecision.id, "charlie", AttestationVerdict.SOUND, 1.0, NOW);
        LedgerAttestation charlieAttestsBob = attestation(
                bobDecision.id, "charlie", AttestationVerdict.SOUND, 0.1, NOW);
        LedgerAttestation aliceAttestsBob = attestation(
                bobDecision.id, "alice", AttestationVerdict.SOUND, 1.0, NOW);
        LedgerAttestation bobAttestsAlice = attestation(
                aliceDecision.id, "bob", AttestationVerdict.SOUND, 1.0, NOW);

        Map<String, Double> scores = runAndCapture(
                List.of(aliceDecision, bobDecision),
                Map.of(aliceDecision.id, List.of(charlieAttestsAlice, bobAttestsAlice),
                       bobDecision.id, List.of(charlieAttestsBob, aliceAttestsBob)),
                List.of());

        double aliceScore = scores.get("alice");
        double bobScore   = scores.get("bob");
        assertTrue(aliceScore > bobScore,
                   "alice (high-confidence attestation from charlie) should score higher than bob (low-confidence): " +
                   "alice=" + aliceScore + ", bob=" + bobScore);
    }

    @Test
    void relevanceFactorAffectsEigenTrustScores() {// With a relevance factor that favours attestations on alice over bob,
// charlie's row normalizes to give alice more trust.
        LedgerEntry aliceDecision = entry("alice");
        LedgerEntry bobDecision   = entry("bob");

        LedgerAttestation charlieAttestsAlice = attestation(
                aliceDecision.id, "charlie", AttestationVerdict.SOUND, 1.0, NOW);
        LedgerAttestation charlieAttestsBob = attestation(
                bobDecision.id, "charlie", AttestationVerdict.SOUND, 1.0, NOW);
        LedgerAttestation aliceAttestsBob = attestation(
                bobDecision.id, "alice", AttestationVerdict.SOUND, 1.0, NOW);
        LedgerAttestation bobAttestsAlice = attestation(
                aliceDecision.id, "bob", AttestationVerdict.SOUND, 1.0, NOW);

        List<LedgerEntry> entries = List.of(aliceDecision, bobDecision);
        Map<UUID, List<LedgerAttestation>> attestations = Map.of(
                aliceDecision.id, List.of(charlieAttestsAlice, bobAttestsAlice),
                bobDecision.id, List.of(charlieAttestsBob, aliceAttestsBob));

// Without relevance factor — symmetric setup → equal scores for alice and bob
        Map<String, Double> noFactorScores = runAndCapture(entries, attestations, List.of());
        assertEquals(noFactorScores.get("alice"), noFactorScores.get("bob"), 1e-9,
                     "without factors, alice and bob should score equally");

// Factor that boosts charlie's attestation on alice's decision, penalises on bob's
        AttestationRelevanceFactor biasedFactor = new AttestationRelevanceFactor() {
            @Override
            public String name() {return "test-bias";}

            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                if ("charlie".equals(ctx.attestorId())) {
                    if ("alice".equals(ctx.scoredActorId())) {
                        return new RelevanceAssessment(1.0, "alice favoured");
                    }
                    return new RelevanceAssessment(0.1, "others penalised");
                }
                return RelevanceAssessment.NEUTRAL;
            }
        };

        Map<String, Double> factoredScores = runAndCapture(
                entries, attestations, List.of(biasedFactor));

        double aliceScore = factoredScores.get("alice");
        double bobScore   = factoredScores.get("bob");
        assertTrue(aliceScore > bobScore,
                   "alice (high relevance from charlie) should score higher than bob (low relevance): " +
                   "alice=" + aliceScore + ", bob=" + bobScore);
    }
}
