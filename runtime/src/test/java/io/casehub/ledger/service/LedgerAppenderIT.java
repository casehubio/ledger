package io.casehub.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.casehub.platform.api.identity.TenancyConstants.DEFAULT_TENANT_ID;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(LedgerAppenderIT.Profile.class)
class LedgerAppenderIT {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "appender-test";
        }
    }

    @Inject LedgerAppender appender;
    @Inject LedgerEntryRepository repo;

    @Test
    void append_persistsPlainLedgerEntry() {
        final UUID subjectId = UUID.randomUUID();
        final AuditRecord record = AuditRecord.event("actor-1", subjectId)
                .withActorRole("orchestrator");
        final UUID id = appender.append(record, DEFAULT_TENANT_ID);
        assertThat(id).isNotNull();

        final var entry = repo.findEntryById(id, DEFAULT_TENANT_ID);
        assertThat(entry).isPresent();
        assertThat(entry.get().actorId).isEqualTo("actor-1");
        assertThat(entry.get().actorRole).isEqualTo("orchestrator");
        assertThat(entry.get().entryType).isEqualTo(LedgerEntryType.EVENT);
        assertThat(entry.get().actorType).isEqualTo(ActorType.AGENT);
        assertThat(entry.get().subjectId).isEqualTo(subjectId);
    }

    @Test
    void append_assignsSequenceNumber() {
        final UUID subjectId = UUID.randomUUID();
        appender.append(AuditRecord.event("a", subjectId), DEFAULT_TENANT_ID);
        final UUID id2 = appender.append(AuditRecord.event("a", subjectId), DEFAULT_TENANT_ID);

        final var entry = repo.findEntryById(id2, DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.sequenceNumber).isEqualTo(2);
    }

    @Test
    void append_commandEntryType() {
        final UUID subjectId = UUID.randomUUID();
        final AuditRecord record = new AuditRecord(subjectId, "actor-2", ActorType.HUMAN,
                "approver", LedgerEntryType.COMMAND, null, null, null, null);
        final UUID id = appender.append(record, DEFAULT_TENANT_ID);

        final var entry = repo.findEntryById(id, DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.entryType).isEqualTo(LedgerEntryType.COMMAND);
        assertThat(entry.actorType).isEqualTo(ActorType.HUMAN);
        assertThat(entry.actorRole).isEqualTo("approver");
    }

    @Test
    void append_causedByEntryId_persisted() {
        final UUID subjectId = UUID.randomUUID();
        final UUID causeId = appender.append(AuditRecord.event("a", subjectId), DEFAULT_TENANT_ID);
        final UUID effectId = appender.append(
                AuditRecord.event("a", subjectId).withCausedBy(causeId), DEFAULT_TENANT_ID);

        final var entry = repo.findEntryById(effectId, DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.causedByEntryId).isEqualTo(causeId);
    }

    @Test
    void append_metadataFlowsToPersistedEntry() {
        final UUID subjectId = UUID.randomUUID();
        final AuditRecord record = AuditRecord.event("actor-meta", subjectId)
                .withMetadata("{\"trigger\":\"sla-breach\"}");
        final UUID id = appender.append(record, DEFAULT_TENANT_ID);

        final var entry = repo.findEntryById(id, DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.metadata).isEqualTo("{\"trigger\":\"sla-breach\"}");
    }

    @Test
    void append_nullMetadata_persistsNull() {
        final UUID subjectId = UUID.randomUUID();
        final UUID id = appender.append(AuditRecord.event("actor-1", subjectId), DEFAULT_TENANT_ID);

        final var entry = repo.findEntryById(id, DEFAULT_TENANT_ID).orElseThrow();
        assertThat(entry.metadata).isNull();
    }

    @Test
    void append_metadataExceedingLimit_throwsIAE() {
        final UUID subjectId = UUID.randomUUID();
        final String oversized = "x".repeat(65537);

        assertThatThrownBy(() -> appender.append(
                AuditRecord.event("actor-1", subjectId).withMetadata(oversized),
                DEFAULT_TENANT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata exceeds maximum size");
    }
}
