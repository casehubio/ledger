package io.casehub.ledger.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;

class AuditRecordTest {

    private final UUID subjectId = UUID.randomUUID();

    // ── Constructor validation ────────────────────────────────────────────────

    @Test
    void rejectsAttestationType() {
        assertThatThrownBy(() -> new AuditRecord(subjectId, "actor", ActorType.AGENT,
                null, LedgerEntryType.ATTESTATION, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ATTESTATION");
    }

    @Test
    void nullActorId_throwsNPE() {
        assertThatThrownBy(() -> new AuditRecord(subjectId, null, ActorType.AGENT,
                null, LedgerEntryType.EVENT, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("actorId");
    }

    @Test
    void nullSubjectId_throwsNPE() {
        assertThatThrownBy(() -> new AuditRecord(null, "actor", ActorType.AGENT,
                null, LedgerEntryType.EVENT, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("subjectId");
    }

    @Test
    void nullActorType_defaultsToAgent() {
        AuditRecord r = new AuditRecord(subjectId, "actor", null,
                null, LedgerEntryType.EVENT, null, null, null, null);
        assertThat(r.actorType()).isEqualTo(ActorType.AGENT);
    }

    @Test
    void nullEntryType_defaultsToEvent() {
        AuditRecord r = new AuditRecord(subjectId, "actor", ActorType.AGENT,
                null, null, null, null, null, null);
        assertThat(r.entryType()).isEqualTo(LedgerEntryType.EVENT);
    }

    @Test
    void commandEntryType_accepted() {
        AuditRecord r = new AuditRecord(subjectId, "actor", ActorType.AGENT,
                null, LedgerEntryType.COMMAND, null, null, null, null);
        assertThat(r.entryType()).isEqualTo(LedgerEntryType.COMMAND);
    }

    // ── Factory method ────────────────────────────────────────────────────────

    @Test
    void eventFactory_setsDefaults() {
        AuditRecord r = AuditRecord.event("actor-1", subjectId);
        assertThat(r.entryType()).isEqualTo(LedgerEntryType.EVENT);
        assertThat(r.actorType()).isEqualTo(ActorType.AGENT);
        assertThat(r.actorRole()).isNull();
        assertThat(r.occurredAt()).isNull();
        assertThat(r.causedByEntryId()).isNull();
        assertThat(r.actorId()).isEqualTo("actor-1");
        assertThat(r.subjectId()).isEqualTo(subjectId);
    }

    // ── With-methods ──────────────────────────────────────────────────────────

    @Test
    void withActorRole_returnsNewInstance() {
        AuditRecord r = AuditRecord.event("a", subjectId);
        AuditRecord r2 = r.withActorRole("reviewer");
        assertThat(r2).isNotSameAs(r);
        assertThat(r2.actorRole()).isEqualTo("reviewer");
        assertThat(r.actorRole()).isNull();
    }

    @Test
    void withActorRole_null_throwsNPE() {
        AuditRecord r = AuditRecord.event("a", subjectId);
        assertThatThrownBy(() -> r.withActorRole(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withCausedBy_returnsNewInstance() {
        UUID causeId = UUID.randomUUID();
        AuditRecord r = AuditRecord.event("a", subjectId);
        AuditRecord r2 = r.withCausedBy(causeId);
        assertThat(r2).isNotSameAs(r);
        assertThat(r2.causedByEntryId()).isEqualTo(causeId);
        assertThat(r.causedByEntryId()).isNull();
    }

    @Test
    void withCausedBy_null_throwsNPE() {
        AuditRecord r = AuditRecord.event("a", subjectId);
        assertThatThrownBy(() -> r.withCausedBy(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withOccurredAt_returnsNewInstance() {
        Instant ts = Instant.parse("2026-07-05T10:00:00Z");
        AuditRecord r = AuditRecord.event("a", subjectId);
        AuditRecord r2 = r.withOccurredAt(ts);
        assertThat(r2).isNotSameAs(r);
        assertThat(r2.occurredAt()).isEqualTo(ts);
        assertThat(r.occurredAt()).isNull();
    }

    @Test
    void withOccurredAt_null_throwsNPE() {
        AuditRecord r = AuditRecord.event("a", subjectId);
        assertThatThrownBy(() -> r.withOccurredAt(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withMethods_preserveOtherFields() {
        Instant ts = Instant.parse("2026-07-05T10:00:00Z");
        UUID causeId = UUID.randomUUID();
        AuditRecord r = AuditRecord.event("actor-1", subjectId)
                .withActorRole("reviewer")
                .withCausedBy(causeId)
                .withOccurredAt(ts);
        assertThat(r.actorId()).isEqualTo("actor-1");
        assertThat(r.subjectId()).isEqualTo(subjectId);
        assertThat(r.actorType()).isEqualTo(ActorType.AGENT);
        assertThat(r.entryType()).isEqualTo(LedgerEntryType.EVENT);
        assertThat(r.actorRole()).isEqualTo("reviewer");
        assertThat(r.causedByEntryId()).isEqualTo(causeId);
        assertThat(r.occurredAt()).isEqualTo(ts);
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Test
    void withMetadata_setsValue() {
        AuditRecord r = AuditRecord.event("actor", subjectId)
                .withMetadata("{\"trigger\":\"sla\"}");
        assertThat(r.metadata()).isEqualTo("{\"trigger\":\"sla\"}");
    }

    @Test
    void withMetadata_preservesOtherFields() {
        AuditRecord r = AuditRecord.event("actor", subjectId)
                .withActorRole("orchestrator")
                .withMetadata("{\"k\":1}");
        assertThat(r.actorId()).isEqualTo("actor");
        assertThat(r.actorRole()).isEqualTo("orchestrator");
        assertThat(r.metadata()).isEqualTo("{\"k\":1}");
    }

    @Test
    void withMetadata_null_throwsNPE() {
        AuditRecord r = AuditRecord.event("actor", subjectId);
        assertThatThrownBy(() -> r.withMetadata(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void eventFactory_metadataIsNull() {
        AuditRecord r = AuditRecord.event("actor", subjectId);
        assertThat(r.metadata()).isNull();
    }
}
