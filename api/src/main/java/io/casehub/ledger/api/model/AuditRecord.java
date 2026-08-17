package io.casehub.ledger.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.casehub.platform.api.identity.ActorType;

/**
 * Input value type for the {@link io.casehub.ledger.api.spi.LedgerAppender} write path.
 *
 * <p>Captures the minimal information needed to append a ledger entry: who did what,
 * on which aggregate, and optionally when and why. The appender creates a concrete
 * {@code LedgerEntry} subclass from this record and delegates to the save pipeline.
 *
 * <p>ATTESTATION entries are not supported — use {@link OutcomeRecord} with
 * {@link io.casehub.ledger.api.spi.OutcomeRecorder} for combined entry + attestation writes.
 *
 * <p>Immutable — all with-methods return new instances.
 */
public record AuditRecord(
        UUID subjectId,
        String actorId,
        ActorType actorType,
        String actorRole,
        LedgerEntryType entryType,
        Instant occurredAt,
        UUID causedByEntryId,
        String metadata,
        Map<String, Object> domainData
) {
    public AuditRecord {
        Objects.requireNonNull(actorId, "actorId required");
        Objects.requireNonNull(subjectId, "subjectId required");
        if (entryType == LedgerEntryType.ATTESTATION) {
            throw new IllegalArgumentException(
                    "AuditRecord does not support ATTESTATION — use OutcomeRecorder");
        }
        if (actorType == null) actorType = ActorType.AGENT;
        if (entryType == null) entryType = LedgerEntryType.EVENT;
    }

    /**
     * Factory for a minimal EVENT record with AGENT actor type.
     *
     * @param actorId the actor identity
     * @param subjectId the aggregate this entry belongs to
     * @return a new AuditRecord with EVENT type and AGENT actor type
     */
    public static AuditRecord event(final String actorId, final UUID subjectId) {
        return new AuditRecord(subjectId, actorId, ActorType.AGENT,
                null, LedgerEntryType.EVENT, null, null, null, null);
    }

    /** @throws NullPointerException if role is null */
    public AuditRecord withActorRole(final String role) {
        return new AuditRecord(subjectId, actorId, actorType,
                Objects.requireNonNull(role, "role"), entryType, occurredAt, causedByEntryId, metadata, domainData);
    }

    /** @throws NullPointerException if entryId is null */
    public AuditRecord withCausedBy(final UUID entryId) {
        return new AuditRecord(subjectId, actorId, actorType,
                actorRole, entryType, occurredAt, Objects.requireNonNull(entryId, "entryId"), metadata, domainData);
    }

    /** @throws NullPointerException if ts is null */
    public AuditRecord withOccurredAt(final Instant ts) {
        return new AuditRecord(subjectId, actorId, actorType,
                actorRole, entryType, Objects.requireNonNull(ts, "ts"), causedByEntryId, metadata, domainData);
    }

    /**
     * Attach consumer-provided freeform JSON context.
     *
     * <p>Must be valid JSON. Must NOT contain personally identifiable information (PII) —
     * the GDPR Art.17 erasure mechanism does not scan field contents.
     *
     * @throws NullPointerException if m is null
     */
    public AuditRecord withMetadata(final String m) {
        return new AuditRecord(subjectId, actorId, actorType,
                actorRole, entryType, occurredAt, causedByEntryId, Objects.requireNonNull(m, "metadata"), domainData);
    }

    public AuditRecord withDomainData(final Map<String, Object> data) {
        return new AuditRecord(subjectId, actorId, actorType,
                actorRole, entryType, occurredAt, causedByEntryId, metadata,
                Objects.requireNonNull(data, "domainData"));
    }
}
