package io.casehub.ledger.api.view;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LedgerEntryView(
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
        Map<String, Object> domainData) {
}
