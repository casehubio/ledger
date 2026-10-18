package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExponentialDecayFunctionTest {


    private static final int HALF_LIFE = 90;
    private static final double PERSISTENCE_MULTIPLIER = 0.5;

    private final ExponentialDecayFunction decay =
            new ExponentialDecayFunction(HALF_LIFE, PERSISTENCE_MULTIPLIER);

    @ParameterizedTest
    @EnumSource(value = AttestationVerdict.class, names = {"FLAGGED", "CHALLENGED"})
    void freshNegativeAttestationHasFullWeight(AttestationVerdict verdict) {
        assertThat(decay.weight(0, verdict)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void flaggedAt90DaysDecaysSlowerThanSound() {
        double soundAt90 = decay.weight(90, AttestationVerdict.SOUND);
        double flaggedAt90 = decay.weight(90, AttestationVerdict.FLAGGED);

        // SOUND half-life = 90 → weight = 0.5
        assertThat(soundAt90).isCloseTo(0.5, within(1e-9));
        // FLAGGED effective half-life = 90/0.5 = 180 → weight = 2^(-90*0.5/90) = 2^(-0.5) ≈ 0.7071
        assertThat(flaggedAt90).isCloseTo(Math.pow(2.0, -0.5), within(1e-6));
    }

    @Test
    void flaggedAt180DaysReachesHalfWeight() {
        // With m=0.5, negative half-life is 180 days: 2^(-180*0.5/90) = 2^(-1) = 0.5
        assertThat(decay.weight(180, AttestationVerdict.FLAGGED)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void uniformDecayWhenMultiplierIsOne() {
        var uniform = new ExponentialDecayFunction(HALF_LIFE, 1.0);

        assertThat(uniform.weight(90, AttestationVerdict.SOUND))
                .isCloseTo(uniform.weight(90, AttestationVerdict.FLAGGED), within(1e-9));
        assertThat(uniform.weight(45, AttestationVerdict.ENDORSED))
                .isCloseTo(uniform.weight(45, AttestationVerdict.CHALLENGED), within(1e-9));
    }

    @ParameterizedTest
    @EnumSource(value = AttestationVerdict.class, names = {"SOUND", "ENDORSED"})
    void positiveVerdictUnchangedByMultiplier(AttestationVerdict verdict) {
        assertThat(decay.weight(0, verdict)).isCloseTo(1.0, within(1e-9));
        assertThat(decay.weight(90, verdict)).isCloseTo(0.5, within(1e-9));
        assertThat(decay.weight(180, verdict)).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void olderNegativeEvidenceHasGreaterInfluenceThanUniformDecay() {
        var asymmetric = new ExponentialDecayFunction(HALF_LIFE, PERSISTENCE_MULTIPLIER);
        var uniform = new ExponentialDecayFunction(HALF_LIFE, 1.0);

        var now = Instant.now();
        var entry = newEntry(now.minus(120, ChronoUnit.DAYS));

        var oldFlagged = new LedgerAttestation();
        oldFlagged.id = UUID.randomUUID();
        oldFlagged.ledgerEntryId = entry.id;
        oldFlagged.attestorId = "reviewer-1";
        oldFlagged.attestorType = ActorType.HUMAN;
        oldFlagged.verdict = AttestationVerdict.FLAGGED;
        oldFlagged.confidence = 1.0;
        oldFlagged.occurredAt = now.minus(120, ChronoUnit.DAYS);

        var decisions = List.of(entry);
        var attestations = Map.of(entry.id, List.of(oldFlagged));

        var asymmetricScore = new TrustScoreComputer(asymmetric).compute(decisions, attestations, now);
        var uniformScore = new TrustScoreComputer(uniform).compute(decisions, attestations, now);

        // Asymmetric decay preserves negative evidence longer → higher beta → lower trust
        assertThat(asymmetricScore.beta()).isGreaterThan(uniformScore.beta());
        assertThat(asymmetricScore.trustScore()).isLessThan(uniformScore.trustScore());
    }

    private static LedgerEntry newEntry(Instant occurredAt) {
        return new LedgerEntry() {{
            this.id = UUID.randomUUID();
            this.subjectId = UUID.randomUUID();
            this.sequenceNumber = 1;
            this.entryType = LedgerEntryType.EVENT;
            this.actorId = "actor-under-test";
            this.actorType = ActorType.AGENT;
            this.occurredAt = occurredAt;
        }};

    }
}
