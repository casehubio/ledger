package io.casehub.ledger.graphql.dto;

import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Input;

@Input("LedgerEntryFilterInput")
public record LedgerEntryFilterInput(
        UUID subjectId,
        String actorId,
        String actorRole,
        Instant from,
        Instant to) {
}
