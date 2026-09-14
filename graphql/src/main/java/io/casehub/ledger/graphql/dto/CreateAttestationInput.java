package io.casehub.ledger.graphql.dto;

import java.util.UUID;
import org.eclipse.microprofile.graphql.Input;

@Input("CreateAttestationInput")
public record CreateAttestationInput(
        UUID entryId,
        String verdict,
        double confidence,
        String capabilityTag) {
}
