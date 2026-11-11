package io.casehub.ledger.api.view;

import java.util.Map;
import java.util.UUID;

public record AppendEntryRequest(
        UUID subjectId,
        String actorId,
        String actorType,
        String entryType,
        String actorRole,
        String metadata,
        Map<String, Object> domainData) {
}
