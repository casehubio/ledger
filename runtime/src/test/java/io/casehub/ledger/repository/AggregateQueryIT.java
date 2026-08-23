package io.casehub.ledger.repository;

import io.casehub.ledger.api.model.AttestationSummary;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.casehub.ledger.api.model.AttestationVerdict.CHALLENGED;
import static io.casehub.ledger.api.model.AttestationVerdict.ENDORSED;
import static io.casehub.ledger.api.model.AttestationVerdict.SOUND;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(AggregateQueryIT.Profile.class)
class AggregateQueryIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "outcome-recorder-test";
        }
    }

    @Inject
    LedgerEntryRepository repo;
    @Inject
    OutcomeRecorder       recorder;

    @Test
    void fulfillmentRate_derivedFromVerdictCounts() {
        final String actor     = "agent-" + UUID.randomUUID();
        final UUID   subjectId = UUID.randomUUID();

        recorder.record(OutcomeRecord.of(actor, subjectId, "routing", ENDORSED, 0.9), DEFAULT_TENANT_ID);
        recorder.record(OutcomeRecord.of(actor, subjectId, "routing", ENDORSED, 0.8), DEFAULT_TENANT_ID);
        recorder.record(OutcomeRecord.of(actor, subjectId, "routing", CHALLENGED, 0.7), DEFAULT_TENANT_ID);

        final var entries = repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(3);

        final Instant earliest = entries.stream().map(e -> e.occurredAt).min(Instant::compareTo).orElseThrow();
        final Map<AttestationVerdict, Long> counts = repo.countByActorAndVerdict(
                actor, earliest.minusSeconds(1), Instant.now().plusSeconds(1), DEFAULT_TENANT_ID);

        assertThat(counts.getOrDefault(ENDORSED, 0L)).isEqualTo(2);
        assertThat(counts.getOrDefault(CHALLENGED, 0L)).isEqualTo(1);

        final double fulfillmentRate = (double) counts.getOrDefault(ENDORSED, 0L)
                                       / (counts.getOrDefault(ENDORSED, 0L) + counts.getOrDefault(CHALLENGED, 0L));
        assertThat(fulfillmentRate).isCloseTo(0.6667, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void perChannelQuality_derivedFromSubjectVerdictCounts() {
        final String actor    = "agent-" + UUID.randomUUID();
        final UUID   channel1 = UUID.randomUUID();
        final UUID   channel2 = UUID.randomUUID();

        recorder.record(OutcomeRecord.of(actor, channel1, "classify", ENDORSED, 0.9), DEFAULT_TENANT_ID);
        recorder.record(OutcomeRecord.of(actor, channel1, "classify", ENDORSED, 0.8), DEFAULT_TENANT_ID);
        recorder.record(OutcomeRecord.of(actor, channel2, "classify", CHALLENGED, 0.6), DEFAULT_TENANT_ID);

        final Instant earliest = repo.findBySubjectId(channel1, DEFAULT_TENANT_ID).stream()
                                     .map(e -> e.occurredAt).min(Instant::compareTo).orElseThrow();
        final Instant queryFrom = earliest.minusSeconds(1);
        final Instant queryTo   = Instant.now().plusSeconds(1);

        final Map<AttestationVerdict, Long> ch1 = repo.countBySubjectAndVerdict(
                channel1, queryFrom, queryTo, DEFAULT_TENANT_ID);
        final Map<AttestationVerdict, Long> ch2 = repo.countBySubjectAndVerdict(
                channel2, queryFrom, queryTo, DEFAULT_TENANT_ID);

        assertThat(ch1.getOrDefault(ENDORSED, 0L)).isEqualTo(2);
        assertThat(ch1.getOrDefault(CHALLENGED, 0L)).isZero();
        assertThat(ch2.getOrDefault(CHALLENGED, 0L)).isEqualTo(1);
    }

    @Test
    void confidenceDistribution_derivedFromSummary() {
        final String actor     = "agent-" + UUID.randomUUID();
        final UUID   subjectId = UUID.randomUUID();

        recorder.record(OutcomeRecord.of(actor, subjectId, "analyse", ENDORSED, 0.6), DEFAULT_TENANT_ID);
        recorder.record(OutcomeRecord.of(actor, subjectId, "analyse", SOUND, 0.9), DEFAULT_TENANT_ID);
        recorder.record(OutcomeRecord.of(actor, subjectId, "analyse", CHALLENGED, 0.3), DEFAULT_TENANT_ID);

        final Instant earliest = repo.findBySubjectId(subjectId, DEFAULT_TENANT_ID).stream()
                                     .map(e -> e.occurredAt).min(Instant::compareTo).orElseThrow();

        final AttestationSummary summary = repo.summariseAttestationsByActor(
                actor, earliest.minusSeconds(1), Instant.now().plusSeconds(1), DEFAULT_TENANT_ID);

        assertThat(summary.totalAttestations()).isEqualTo(3);
        assertThat(summary.meanConfidence()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.01));
        assertThat(summary.minConfidence()).isEqualTo(0.3);
        assertThat(summary.maxConfidence()).isEqualTo(0.9);
        assertThat(summary.verdictCounts()).containsEntry(ENDORSED, 1L);
        assertThat(summary.verdictCounts()).containsEntry(SOUND, 1L);
        assertThat(summary.verdictCounts()).containsEntry(CHALLENGED, 1L);
    }

    @Test
    void countByActorAndVerdict_emptyWhenNoAttestations() {
        final Map<AttestationVerdict, Long> counts = repo.countByActorAndVerdict(
                "nonexistent-" + UUID.randomUUID(),
                Instant.now().minusSeconds(3600), Instant.now(), DEFAULT_TENANT_ID);
        assertThat(counts).isEmpty();
    }

    @Test
    void summariseAttestationsByActor_emptyWhenNoAttestations() {
        final AttestationSummary summary = repo.summariseAttestationsByActor(
                "nonexistent-" + UUID.randomUUID(),
                Instant.now().minusSeconds(3600), Instant.now(), DEFAULT_TENANT_ID);
        assertThat(summary.totalAttestations()).isZero();
        assertThat(summary.meanConfidence()).isEqualTo(0.0);
    }
}
