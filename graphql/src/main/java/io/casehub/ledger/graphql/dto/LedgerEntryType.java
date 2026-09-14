package io.casehub.ledger.graphql.dto;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.platform.graphql.scalar.Json;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Type;

@Type("LedgerEntry")
public record LedgerEntryType(
        UUID id,
        UUID subjectId,
        String tenancyId,
        int sequenceNumber,
        String entryType,
        String actorId,
        String actorType,
        String actorRole,
        Instant occurredAt,
        String digest,
        String traceId,
        UUID causedByEntryId,
        String metadata,
        Json domainData) {

    public static LedgerEntryType from(LedgerEntry entry) {
        return new LedgerEntryType(
                entry.id,
                entry.subjectId,
                entry.tenancyId,
                entry.sequenceNumber,
                entry.entryType != null ? entry.entryType.name() : null,
                entry.actorId,
                entry.actorType != null ? entry.actorType.name() : null,
                entry.actorRole,
                entry.occurredAt,
                entry.digest,
                entry.traceId,
                entry.causedByEntryId,
                entry.metadata,
                entry.domainData != null ? Json.of(entry.domainData) : null);
    }
}
