package io.casehub.ledger.repository;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.OutcomeRecord;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.casehub.ledger.api.model.AttestationVerdict.SOUND;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(StreamingQueryIT.Profile.class)
class StreamingQueryIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "outcome-recorder-test";
        }
    }

    @Inject LedgerEntryRepository repo;
    @Inject OutcomeRecorder recorder;

    @Test
    void streamBySubjectId_returnsEntriesInSequenceOrder() {
        final UUID subjectId = UUID.randomUUID();
        final String actor = "agent-" + UUID.randomUUID();

        recorder.record(OutcomeRecord.of(actor, subjectId, "routing", SOUND, 0.8));
        recorder.record(OutcomeRecord.of(actor, subjectId, "routing", SOUND, 0.9));
        recorder.record(OutcomeRecord.of(actor, subjectId, "routing", SOUND, 0.7));

        try (Stream<LedgerEntry> stream = repo.streamBySubjectId(subjectId, DEFAULT_TENANT_ID)) {
            final List<LedgerEntry> entries = stream.toList();
            assertThat(entries).hasSize(3);
            assertThat(entries.get(0).sequenceNumber).isLessThan(entries.get(1).sequenceNumber);
            assertThat(entries.get(1).sequenceNumber).isLessThan(entries.get(2).sequenceNumber);
        }
    }

    @Test
    void streamByActorId_filtersOnTimeRange() {
        final UUID subjectId = UUID.randomUUID();
        final String actor = "agent-" + UUID.randomUUID();
        final Instant now = Instant.now();

        recorder.record(OutcomeRecord.of(actor, subjectId, "classify", SOUND, 0.8)
                .withOccurredAt(now.minusSeconds(3600)));
        recorder.record(OutcomeRecord.of(actor, subjectId, "classify", SOUND, 0.9)
                .withOccurredAt(now.minusSeconds(1800)));
        recorder.record(OutcomeRecord.of(actor, subjectId, "classify", SOUND, 0.7)
                .withOccurredAt(now.plusSeconds(3600)));

        try (Stream<LedgerEntry> stream = repo.streamByActorId(
                actor, now.minusSeconds(7200), now, DEFAULT_TENANT_ID)) {
            final List<LedgerEntry> entries = stream.toList();
            assertThat(entries).hasSize(2);
        }
    }

    @Test
    void findBySubjectIdPaged_returnsPagesCorrectly() {
        final UUID subjectId = UUID.randomUUID();
        final String actor = "agent-" + UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            recorder.record(OutcomeRecord.of(actor, subjectId, "routing", SOUND, 0.8));
        }

        final List<LedgerEntry> page1 = repo.findBySubjectIdPaged(
                subjectId, 0, 2, DEFAULT_TENANT_ID);
        assertThat(page1).hasSize(2);

        final List<LedgerEntry> page2 = repo.findBySubjectIdPaged(
                subjectId, page1.get(1).sequenceNumber, 2, DEFAULT_TENANT_ID);
        assertThat(page2).hasSize(2);

        final List<LedgerEntry> page3 = repo.findBySubjectIdPaged(
                subjectId, page2.get(1).sequenceNumber, 2, DEFAULT_TENANT_ID);
        assertThat(page3).hasSize(1);
    }

    @Test
    void streamBySubjectId_emptyForUnknownSubject() {
        try (Stream<LedgerEntry> stream = repo.streamBySubjectId(
                UUID.randomUUID(), DEFAULT_TENANT_ID)) {
            assertThat(stream.count()).isZero();
        }
    }
}
