package io.casehub.ledger.rest.dto;

import java.util.UUID;

public record VerificationResponse(
        UUID subjectId,
        String treeRoot,
        boolean verified) {
}
