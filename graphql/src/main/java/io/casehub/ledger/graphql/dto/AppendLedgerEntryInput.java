package io.casehub.ledger.graphql.dto;

import io.casehub.platform.graphql.scalar.Json;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Input;

@Input("AppendLedgerEntryInput")
public record AppendLedgerEntryInput(
        UUID subjectId,
        String actorId,
        String actorRole,
        String entryType,
        String metadata,
        Json domainData) {
}
