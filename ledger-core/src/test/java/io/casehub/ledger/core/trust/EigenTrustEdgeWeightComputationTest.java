package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EigenTrustEdgeWeightComputationTest {

    @Test
    void computesProductOfThreeFactors() {
        Instant now = Instant.now();
        UUID attId = UUID.randomUUID();

        var att = new LedgerAttestation();
        att.id = attId;
        att.verdict = AttestationVerdict.SOUND;
        att.confidence = 0.8;
        att.occurredAt = now.minus(10, ChronoUnit.DAYS);

        double decayWeight = 0.5;
        double relevanceWeight = 0.7;

        DecayFunction decay = (age, verdict) -> decayWeight;
        Map<UUID, Double> relevance = Map.of(attId, relevanceWeight);

        Map<UUID, Double> weights = EigenTrustEdgeWeights.compute(
                List.of(att), decay, relevance, now);

        // decay × confidence × relevance = 0.5 × 0.8 × 0.7 = 0.28
        assertEquals(0.28, weights.get(attId), 1e-9);
    }

    @Test
    void missingRelevanceDefaultsToOne() {
        Instant now = Instant.now();
        UUID attId = UUID.randomUUID();

        var att = new LedgerAttestation();
        att.id = attId;
        att.verdict = AttestationVerdict.SOUND;
        att.confidence = 0.9;
        att.occurredAt = now;

        DecayFunction decay = (age, verdict) -> 1.0;

        Map<UUID, Double> weights = EigenTrustEdgeWeights.compute(
                List.of(att), decay, Map.of(), now);

        // decay(1.0) × confidence(0.9) × relevance(1.0) = 0.9
        assertEquals(0.9, weights.get(attId), 1e-9);
    }

    @Test
    void zeroConfidenceProducesZeroWeight() {
        Instant now = Instant.now();
        UUID attId = UUID.randomUUID();

        var att = new LedgerAttestation();
        att.id = attId;
        att.verdict = AttestationVerdict.SOUND;
        att.confidence = 0.0;
        att.occurredAt = now;

        DecayFunction decay = (age, verdict) -> 1.0;

        Map<UUID, Double> weights = EigenTrustEdgeWeights.compute(
                List.of(att), decay, Map.of(), now);

        assertEquals(0.0, weights.get(attId), 1e-9);
    }

    @Test
    void futureOccurredAtClampedToZeroAge() {
        Instant now   = Instant.now();
        UUID    attId = UUID.randomUUID();

        var att = new LedgerAttestation();
        att.id         = attId;
        att.verdict    = AttestationVerdict.SOUND;
        att.confidence = 1.0;
        att.occurredAt = now.plus(2, ChronoUnit.DAYS);

        boolean[] decayCalled = {false};
        DecayFunction decay = (age, verdict) -> {
            decayCalled[0] = true;
            assertTrue(age >= 0, "age should be clamped to >= 0, was " + age);
            return 0.9;
        };

        Map<UUID, Double> weights = EigenTrustEdgeWeights.compute(
                List.of(att), decay, Map.of(), now);

        assertTrue(decayCalled[0]);
        assertEquals(0.9, weights.get(attId), 1e-9);
    }
}
