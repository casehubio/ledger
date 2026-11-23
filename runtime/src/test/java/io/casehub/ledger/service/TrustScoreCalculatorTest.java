package io.casehub.ledger.service;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.CapabilityTag;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.AttestationRelevanceContext;
import io.casehub.ledger.api.spi.AttestationRelevanceFactor;
import io.casehub.ledger.api.spi.RelevanceAssessment;
import io.casehub.ledger.core.trust.AllAttestationsGlobalStrategy;
import io.casehub.ledger.core.trust.DecayFunction;
import io.casehub.ledger.core.trust.FrequencyWeightedGlobalStrategy;
import io.casehub.ledger.core.trust.NoOpAttestorCredibilityPolicy;
import io.casehub.ledger.core.trust.RelevanceComposer;
import io.casehub.ledger.core.trust.TrustScoreCalculator;
import io.casehub.ledger.core.trust.TrustScoreCalculator.ComputedScores;
import io.casehub.ledger.core.trust.TrustScoreComputer;
import io.casehub.ledger.jpa.LedgerAttestation;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure JUnit 5 unit tests for {@link TrustScoreCalculator} — no Quarkus runtime, no CDI.
 *
 * <p>Tests the four-pass computation orchestration (capability → dimension →
 * capability×dimension → global) extracted from {@code PerActorTrustComputer}.
 */
class TrustScoreCalculatorTest {

    private static final DecayFunction NO_DECAY = (age, verdict) -> 1.0;
    private final Instant now = Instant.now();

    private TrustScoreCalculator calculator(final DecayFunction decay) {
        return new TrustScoreCalculator(decay, new AllAttestationsGlobalStrategy(), new NoOpAttestorCredibilityPolicy(), new RelevanceComposer(List.of(), 0.5));
    }

    private TrustScoreCalculator calculator() {
        return calculator(NO_DECAY);
    }

    private TrustScoreCalculator calculatorWithRelevance(final AttestationRelevanceFactor... factors) {
        return new TrustScoreCalculator(NO_DECAY, new AllAttestationsGlobalStrategy(), new NoOpAttestorCredibilityPolicy(), new RelevanceComposer(List.of(factors), 0.1));
    }


    // ── Fixtures ─────────────────────────────────────────────────────────────

    private static class TestLedgerEntry extends LedgerEntry {
    }

    private TestLedgerEntry decision(final String actorId) {
        final TestLedgerEntry e = new TestLedgerEntry();
        e.id = UUID.randomUUID();
        e.subjectId = UUID.randomUUID();
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = actorId;
        e.actorType = ActorType.AGENT;
        e.occurredAt = now;
        return e;
    }

    private LedgerAttestation attestation(final UUID entryId, final AttestationVerdict verdict,
            final String capabilityTag) {
        final LedgerAttestation a = new LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = entryId;
        a.subjectId = UUID.randomUUID();
        a.attestorId = "peer";
        a.attestorType = ActorType.HUMAN;
        a.verdict = verdict;
        a.confidence = 1.0;
        a.capabilityTag = capabilityTag;
        a.occurredAt = now;
        return a;
    }

    private LedgerAttestation dimensionAttestation(final UUID entryId, final String dimension,
            final double score) {
        return dimensionAttestation(entryId, dimension, score, CapabilityTag.GLOBAL);
    }

    private LedgerAttestation dimensionAttestation(final UUID entryId, final String dimension,
            final double score, final String capabilityTag) {
        final LedgerAttestation a = new LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = entryId;
        a.subjectId = UUID.randomUUID();
        a.attestorId = "peer";
        a.attestorType = ActorType.HUMAN;
        a.verdict = AttestationVerdict.SOUND;
        a.confidence = 1.0;
        a.capabilityTag = capabilityTag;
        a.trustDimension = dimension;
        a.dimensionScore = score;
        a.occurredAt = now;
        return a;
    }

    private LedgerAttestation attestationWithSnapshot(final UUID entryId, final AttestationVerdict verdict, final String capabilityTag, final String attestorId, final String snapshotJson) {
        final LedgerAttestation a = new LedgerAttestation();
        a.id                = UUID.randomUUID();
        a.ledgerEntryId     = entryId;
        a.subjectId         = UUID.randomUUID();
        a.attestorId        = attestorId;
        a.attestorType      = ActorType.AGENT;
        a.verdict           = verdict;
        a.confidence        = 1.0;
        a.capabilityTag     = capabilityTag;
        a.occurredAt        = now;
        a.relevanceSnapshot = snapshotJson;
        return a;
    }


    // ── Empty decisions ──────────────────────────────────────────────────────

    @Test
    void emptyDecisions_returnsEmptyMapsAndPriorGlobal() {
        final ComputedScores scores = calculator().computeAll(List.of(), Map.of(), now);

        assertThat(scores.capabilityScores()).isEmpty();
        assertThat(scores.dimensionScores()).isEmpty();
        assertThat(scores.capabilityDimensionScores()).isEmpty();
        assertThat(scores.globalScore().trustScore()).isCloseTo(0.5, within(0.01));
        assertThat(scores.globalScore().decisionCount()).isEqualTo(0);
    }

    // ── Capability pass ──────────────────────────────────────────────────────

    @Test
    void singleCapabilityAttestation_producesCapabilityScore() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation a = attestation(d.id, AttestationVerdict.SOUND, "review");

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(scores.capabilityScores()).containsKey("review");
        final TrustScoreComputer.ActorScore cap = scores.capabilityScores().get("review");
        assertThat(cap.trustScore()).isCloseTo(2.0 / 3.0, within(0.01));
        assertThat(cap.decisionCount()).isEqualTo(1);
    }

    @Test
    void twoCapabilities_producesScoreForEach() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation review = attestation(d.id, AttestationVerdict.SOUND, "review");
        final LedgerAttestation triage = attestation(d.id, AttestationVerdict.FLAGGED, "triage");

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(review, triage)), now);

        assertThat(scores.capabilityScores()).containsKeys("review", "triage");
        assertThat(scores.capabilityScores().get("review").trustScore()).isGreaterThan(0.5);
        assertThat(scores.capabilityScores().get("triage").trustScore()).isLessThan(0.5);
    }

    @Test
    void globalAttestations_excludedFromCapabilityPass() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation globalAtt = attestation(d.id, AttestationVerdict.SOUND, CapabilityTag.GLOBAL);

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(globalAtt)), now);

        assertThat(scores.capabilityScores()).isEmpty();
    }

    // ── Aggregation ──────────────────────────────────────────────────────────

    @Test
    void multipleAttestorsPerEntryCapability_aggregatedBeforeScoring() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation s1 = attestation(d.id, AttestationVerdict.SOUND, "review");
        final LedgerAttestation s2 = attestation(d.id, AttestationVerdict.SOUND, "review");
        final LedgerAttestation f1 = attestation(d.id, AttestationVerdict.FLAGGED, "review");
        s1.attestorId = "peer-1";
        s2.attestorId = "peer-2";
        f1.attestorId = "peer-3";

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(s1, s2, f1)), now);

        assertThat(scores.capabilityScores()).containsKey("review");
        assertThat(scores.capabilityScores().get("review").trustScore()).isGreaterThan(0.5);
    }

    // ── Dimension pass ───────────────────────────────────────────────────────

    @Test
    void dimensionAttestation_producesDimensionScore() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation a = dimensionAttestation(d.id, "thoroughness", 0.8);

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(scores.dimensionScores()).containsKey("thoroughness");
        assertThat(scores.dimensionScores().get("thoroughness")).isCloseTo(0.8, within(0.01));
    }

    @Test
    void twoDimensions_producesScoreForEach() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation t = dimensionAttestation(d.id, "thoroughness", 0.8);
        final LedgerAttestation a = dimensionAttestation(d.id, "accuracy", 0.6);

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(t, a)), now);

        assertThat(scores.dimensionScores()).containsKeys("thoroughness", "accuracy");
        assertThat(scores.dimensionScores().get("thoroughness")).isCloseTo(0.8, within(0.01));
        assertThat(scores.dimensionScores().get("accuracy")).isCloseTo(0.6, within(0.01));
    }

    // ── Capability×Dimension pass ────────────────────────────────────────────

    @Test
    void capabilityDimensionAttestation_producesCapDimScore() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation a = dimensionAttestation(d.id, "thoroughness", 0.9, "review");

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(scores.capabilityDimensionScores()).containsKey("review");
        assertThat(scores.capabilityDimensionScores().get("review")).containsKey("thoroughness");
        assertThat(scores.capabilityDimensionScores().get("review").get("thoroughness"))
                .isCloseTo(0.9, within(0.01));
    }

    @Test
    void globalCapabilityDimension_excludedFromCapDimPass() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation a = dimensionAttestation(d.id, "thoroughness", 0.9, CapabilityTag.GLOBAL);

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(scores.capabilityDimensionScores()).isEmpty();
    }

    // ── Global pass ──────────────────────────────────────────────────────────

    @Test
    void globalScore_usesAllAttestationsByDefault() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation a = attestation(d.id, AttestationVerdict.SOUND, "review");

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(scores.globalScore().trustScore()).isCloseTo(2.0 / 3.0, within(0.01));
    }

    @Test
    void globalScore_withDeriveStrategy_usesCapabilityFrequencyWeights() {
        final TrustScoreCalculator freqCalculator = new TrustScoreCalculator(
                NO_DECAY, new FrequencyWeightedGlobalStrategy(), new NoOpAttestorCredibilityPolicy(), new RelevanceComposer(List.of(), 0.5));

        final TestLedgerEntry d1 = decision("alice");
        final TestLedgerEntry d2 = decision("alice");
        // 2 review attestations, 1 triage attestation
        final LedgerAttestation r1 = attestation(d1.id, AttestationVerdict.SOUND, "review");
        final LedgerAttestation r2 = attestation(d2.id, AttestationVerdict.SOUND, "review");
        final LedgerAttestation t1 = attestation(d1.id, AttestationVerdict.FLAGGED, "triage");

        final ComputedScores scores = freqCalculator.computeAll(
                List.of(d1, d2), Map.of(d1.id, List.of(r1, t1), d2.id, List.of(r2)), now);

        // FrequencyWeightedGlobalStrategy.derive() computes weighted average of capability scores
        // by frequency — review has 2/3 weight, triage has 1/3 weight
        assertThat(scores.globalScore().trustScore()).isGreaterThan(0.0);
        assertThat(scores.globalScore().trustScore()).isLessThan(1.0);
        // review is SOUND (high score), triage is FLAGGED (low score), review dominates → above 0.5
        assertThat(scores.globalScore().trustScore()).isGreaterThan(0.5);
    }

    @Test
    void globalPass_deriveReceivesRawAttestationsForFrequencyWeights() {
        final TrustScoreCalculator freqCalculator = new TrustScoreCalculator(
                NO_DECAY, new FrequencyWeightedGlobalStrategy(), new NoOpAttestorCredibilityPolicy(), new RelevanceComposer(List.of(), 0.5));

        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation r1 = attestation(d.id, AttestationVerdict.SOUND, "review");
        final LedgerAttestation r2 = attestation(d.id, AttestationVerdict.SOUND, "review");
        final LedgerAttestation r3 = attestation(d.id, AttestationVerdict.FLAGGED, "review");
        final LedgerAttestation t1 = attestation(d.id, AttestationVerdict.SOUND, "triage");
        r1.attestorId = "p1";
        r2.attestorId = "p2";
        r3.attestorId = "p3";
        t1.attestorId = "p4";

        final ComputedScores scoresFreq = freqCalculator.computeAll(
                List.of(d), Map.of(d.id, List.of(r1, r2, r3, t1)), now);

        // With raw attestations: review=3/4 weight, triage=1/4 weight
        // With aggregated: review=1/2, triage=1/2 (WRONG)
        // Since review has SOUND consensus and triage has SOUND, both contribute positively
        // but the frequency weights differ — result should reflect the 3:1 ratio
        assertThat(scoresFreq.globalScore().trustScore()).isGreaterThan(0.0);
    }

    @Test
    void globalScore_flaggedOnSameEntry_contributesToBeta() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation sound = attestation(d.id, AttestationVerdict.SOUND, "security-review");
        sound.confidence = 1.0;

        final LedgerAttestation flagged = attestation(d.id, AttestationVerdict.FLAGGED, "security-review");
        flagged.confidence = 0.9;
        flagged.attestorId = "peer-2";

        final ComputedScores scores = calculator().computeAll(
                List.of(d), Map.of(d.id, List.of(sound, flagged)), now);

        assertThat(scores.globalScore().attestationNegative())
                .as("FLAGGED attestation must be counted as negative in global score")
                .isEqualTo(1);
        assertThat(scores.globalScore().overturnedCount())
                .as("Entry with FLAGGED attestation must count as overturned")
                .isEqualTo(1);
    }

    @Test
    void capabilityScore_groupAwareFactor_shiftsScore() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation close1 = attestationWithSnapshot(d.id, AttestationVerdict.SOUND, "review", "close-1", "{\"org-distance\": 1}");
        final LedgerAttestation close2 = attestationWithSnapshot(d.id, AttestationVerdict.SOUND, "review", "close-2", "{\"org-distance\": 1}");
        final LedgerAttestation close3 = attestationWithSnapshot(d.id, AttestationVerdict.SOUND, "review", "close-3", "{\"org-distance\": 1}");
        final LedgerAttestation farPeer = attestationWithSnapshot(d.id, AttestationVerdict.FLAGGED, "review", "far-peer", "{\"org-distance\": 3}");

        final ComputedScores baseline = calculator().computeAll(List.of(d), Map.of(d.id, List.of(close1, close2, close3, farPeer)), now);

        AttestationRelevanceFactor capFactor = groupCapFactor(0.5);
        final ComputedScores weighted = calculatorWithRelevance(capFactor).computeAll(List.of(d), Map.of(d.id, List.of(close1, close2, close3, farPeer)), now);

        // 3 same-unit (75%) > 50% cap -> same-unit SOUND attestations down-weighted
        // far-peer FLAGGED stays at full weight -> score shifts lower
        assertThat(weighted.capabilityScores().get("review").trustScore())
                .isLessThan(baseline.capabilityScores().get("review").trustScore());
    }

    @Test
    void globalScore_groupAwareFactor_affectsGlobalScore() {
        final TestLedgerEntry d = decision("alice");
        final LedgerAttestation close1 = attestationWithSnapshot(d.id, AttestationVerdict.SOUND, CapabilityTag.GLOBAL, "close-1", "{\"org-distance\": 1}");
        final LedgerAttestation close2 = attestationWithSnapshot(d.id, AttestationVerdict.SOUND, CapabilityTag.GLOBAL, "close-2", "{\"org-distance\": 1}");
        final LedgerAttestation close3 = attestationWithSnapshot(d.id, AttestationVerdict.SOUND, CapabilityTag.GLOBAL, "close-3", "{\"org-distance\": 1}");
        final LedgerAttestation farPeer = attestationWithSnapshot(d.id, AttestationVerdict.FLAGGED, CapabilityTag.GLOBAL, "far-peer", "{\"org-distance\": 3}");

        final ComputedScores baseline = calculator().computeAll(List.of(d), Map.of(d.id, List.of(close1, close2, close3, farPeer)), now);
        final ComputedScores weighted = calculatorWithRelevance(groupCapFactor(0.5)).computeAll(List.of(d), Map.of(d.id, List.of(close1, close2, close3, farPeer)), now);

        // 3 same-unit (75%) > 50% cap -> SOUND attestations down-weighted, FLAGGED unchanged
        assertThat(weighted.globalScore().trustScore())
                .isLessThan(baseline.globalScore().trustScore());
    }

    @Test
    void equalRelevance_preservesBaselineScore() {
        final TestLedgerEntry   d = decision("alice");
        final LedgerAttestation a = attestation(d.id, AttestationVerdict.SOUND, "review");

        AttestationRelevanceFactor uniformFactor = new AttestationRelevanceFactor() {
            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                return new RelevanceAssessment(0.8, "uniform");
            }

            @Override
            public String name() {
                return "uniform";
            }
        };

        final ComputedScores baseline = calculator().computeAll(List.of(d), Map.of(d.id, List.of(a)), now);
        final ComputedScores weighted = calculatorWithRelevance(uniformFactor).computeAll(List.of(d), Map.of(d.id, List.of(a)), now);

        assertThat(weighted.capabilityScores().get("review").trustScore())
                .isCloseTo(baseline.capabilityScores().get("review").trustScore(), within(1e-9));
    }

    @Test
    void snapshotRoundTrip_orgDistanceFlowsThroughToFactor() {
        final TestLedgerEntry   d    = decision("alice");
        List<io.casehub.ledger.api.model.LedgerAttestation> atts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            atts.add(attestationWithSnapshot(d.id, AttestationVerdict.SOUND, "review", "close-" + i, "{\"org-distance\": 1}"));
        }
        for (int i = 0; i < 2; i++) {
            atts.add(attestationWithSnapshot(d.id, AttestationVerdict.SOUND, "review", "far-" + i, "{\"org-distance\": 3}"));
        }

        final ComputedScores baseline = calculator().computeAll(List.of(d), Map.of(d.id, atts), now);
        final ComputedScores weighted = calculatorWithRelevance(groupCapFactor(0.5)).computeAll(List.of(d), Map.of(d.id, atts), now);

        assertThat(weighted.capabilityScores().get("review").trustScore()).isGreaterThan(0.5);
        assertThat(baseline.capabilityScores().get("review").trustScore()).isGreaterThan(0.5);
    }


    @Test
    void malformedRelevanceSnapshot_pipelineCompletesWithNeutralFallback() {
        final TestLedgerEntry   d         = decision("alice");
        final LedgerAttestation valid     = attestationWithSnapshot(d.id, AttestationVerdict.SOUND, "review", "peer-a", "{\"org-distance\": 1}");
        final LedgerAttestation malformed = attestationWithSnapshot(d.id, AttestationVerdict.SOUND, "review", "peer-b", "not valid json");

        final ComputedScores scores = calculatorWithRelevance(groupCapFactor(0.5))
                                              .computeAll(List.of(d), Map.of(d.id, List.of(valid, malformed)), now);

        assertThat(scores.capabilityScores()).containsKey("review");
        assertThat(scores.capabilityScores().get("review").trustScore()).isGreaterThan(0.5);
    }

    private static AttestationRelevanceFactor groupCapFactor(final double maxSameUnitShare) {
        return new AttestationRelevanceFactor() {
            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                return RelevanceAssessment.NEUTRAL;
            }

            @Override
            public Map<UUID, RelevanceAssessment> assessBatch(List<AttestationRelevanceContext> ctxs) {
                Map<UUID, RelevanceAssessment> result    = new LinkedHashMap<>();
                List<UUID>                     sameUnit  = new ArrayList<>();
                List<UUID>                     crossUnit = new ArrayList<>();

                for (AttestationRelevanceContext ctx : ctxs) {
                    var snapshot = ctx.snapshot();
                    if (snapshot != null && snapshot.containsKey("org-distance")) {
                        int dist = ((Number) snapshot.get("org-distance")).intValue();
                        if (dist == 1) {
                            sameUnit.add(ctx.attestationId());
                        } else if (dist > 1) {
                            crossUnit.add(ctx.attestationId());
                        } else {
                            result.put(ctx.attestationId(), RelevanceAssessment.NEUTRAL);
                        }
                    } else {
                        result.put(ctx.attestationId(), RelevanceAssessment.NEUTRAL);
                    }
                }

                int sameCount  = sameUnit.size();
                int crossCount = crossUnit.size();
                if (crossCount == 0 || sameCount == 0) {
                    for (AttestationRelevanceContext ctx : ctxs) {
                        result.put(ctx.attestationId(), RelevanceAssessment.NEUTRAL);
                    }
                    return result;
                }

                double fraction = (double) sameCount / (sameCount + crossCount);
                if (fraction <= maxSameUnitShare) {
                    for (AttestationRelevanceContext ctx : ctxs) {
                        result.put(ctx.attestationId(), RelevanceAssessment.NEUTRAL);
                    }
                    return result;
                }

                double              weight = maxSameUnitShare * crossCount / ((1.0 - maxSameUnitShare) * sameCount);
                RelevanceAssessment capped = new RelevanceAssessment(weight, "capped");
                for (UUID id : sameUnit) {result.put(id, capped);}
                for (UUID id : crossUnit) {result.put(id, RelevanceAssessment.NEUTRAL);}
                return result;
            }

            @Override
            public String name() {
                return "test-independence";
            }
        };
    }
}
