package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrustScoreComputerRelevanceTest {

    private static final Instant NOW = Instant.now();
    private final TrustScoreComputer computer = new TrustScoreComputer(90);

    private static class TestEntry extends LedgerEntry {}

    private static TestEntry decision(String actorId) {
        TestEntry e = new TestEntry();
        e.id = UUID.randomUUID();
        e.actorId = actorId;
        return e;
    }

    private static LedgerAttestation attestation(UUID entryId, String attestorId, AttestationVerdict verdict) {
        LedgerAttestation a = new LedgerAttestation();
        a.id = UUID.randomUUID();
        a.ledgerEntryId = entryId;
        a.attestorId = attestorId;
        a.verdict = verdict;
        a.confidence = 1.0;
        a.occurredAt = NOW;
        return a;
    }

    @Test
    void neutralWeights_sameScoreAsNoWeights() {
        TestEntry d = decision("actor-1");
        LedgerAttestation a1 = attestation(d.id, "peer-1", AttestationVerdict.SOUND);
        LedgerAttestation a2 = attestation(d.id, "peer-2", AttestationVerdict.SOUND);
        LedgerAttestation a3 = attestation(d.id, "peer-3", AttestationVerdict.FLAGGED);

        var byEntry = Map.of(d.id, List.of(a1, a2, a3));
        var decisions = List.<LedgerEntry>of(d);

        var scoreNeutral = computer.compute(decisions, byEntry, NOW, AttestationWeights.NEUTRAL);
        var scoreWithUniform = computer.compute(decisions, byEntry, NOW,
                new AttestationWeights(Map.of(),
                        Map.of(a1.id, 0.7, a2.id, 0.7, a3.id, 0.7)));

        assertEquals(scoreNeutral.trustScore(), scoreWithUniform.trustScore(), 1e-9,
                "uniform relevance must not change score (mass-preserving)");
    }

    @Test
    void selfAttestation_excluded() {
        TestEntry d = decision("actor-1");
        LedgerAttestation selfAtt = attestation(d.id, "actor-1", AttestationVerdict.SOUND);
        LedgerAttestation peerAtt = attestation(d.id, "peer-1", AttestationVerdict.FLAGGED);

        var byEntry = Map.of(d.id, List.of(selfAtt, peerAtt));
        var decisions = List.<LedgerEntry>of(d);

        var score = computer.compute(decisions, byEntry, NOW, AttestationWeights.NEUTRAL);

        assertEquals(0, score.attestationPositive(),
                "self-attestation excluded — SOUND from actor-1 on own decision not counted");
        assertEquals(1, score.attestationNegative(),
                "only the FLAGGED peer attestation counts");
    }

    @Test
    void mixedRelevance_highRelevanceWeighedMore() {
        TestEntry d = decision("actor-1");
        LedgerAttestation sameUnit = attestation(d.id, "peer-1", AttestationVerdict.SOUND);
        LedgerAttestation crossUnit = attestation(d.id, "peer-2", AttestationVerdict.FLAGGED);

        var byEntry = Map.of(d.id, List.of(sameUnit, crossUnit));
        var decisions = List.<LedgerEntry>of(d);

        var scoreEqual = computer.compute(decisions, byEntry, NOW, AttestationWeights.NEUTRAL);
        var scoreWeighted = computer.compute(decisions, byEntry, NOW,
                new AttestationWeights(Map.of(),
                        Map.of(sameUnit.id, 1.0, crossUnit.id, 0.5)));

        assertTrue(scoreWeighted.trustScore() > scoreEqual.trustScore(),
                "weighting SOUND higher should increase score");
    }

    @Test
    void zeroCredibility_allAttestations_returnsDefaultScore() {
        TestEntry d = decision("actor-1");
        LedgerAttestation a1 = attestation(d.id, "peer-1", AttestationVerdict.SOUND);

        var zeroCredibility = Map.of("peer-1",
                new io.casehub.ledger.api.spi.AttestorCredibilityPolicy.CredibilityAssessment(
                        0.0, null, java.util.Set.of()));
        var byEntry = Map.of(d.id, List.of(a1));
        var decisions = List.<LedgerEntry>of(d);

        var score = computer.compute(decisions, byEntry, NOW,
                new AttestationWeights(zeroCredibility, Map.of()));

        assertEquals(0.5, score.trustScore(), 1e-9, "zero credibility → default 0.5");
        assertFalse(Double.isNaN(score.trustScore()), "must not be NaN");
    }

    @Test
    void dimensionScore_relevanceApplied() {
        LedgerAttestation a1 = attestation(UUID.randomUUID(), "peer-1", AttestationVerdict.SOUND);
        a1.dimensionScore = 0.9;
        a1.trustDimension = "accuracy";

        LedgerAttestation a2 = attestation(UUID.randomUUID(), "peer-2", AttestationVerdict.SOUND);
        a2.dimensionScore = 0.3;
        a2.trustDimension = "accuracy";

        var scoreEqual = computer.computeDimensionScore(List.of(a1, a2), NOW, AttestationWeights.NEUTRAL);
        var scoreWeighted = computer.computeDimensionScore(List.of(a1, a2), NOW,
                new AttestationWeights(Map.of(),
                        Map.of(a1.id, 1.0, a2.id, 0.5)));

        assertTrue(scoreEqual.isPresent());
        assertTrue(scoreWeighted.isPresent());
        assertTrue(scoreWeighted.getAsDouble() > scoreEqual.getAsDouble(),
                "higher relevance on the 0.9 attestation should raise the weighted average");
    }
}
