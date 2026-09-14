package io.casehub.ledger.api.view;

import java.util.UUID;

public record VerificationView(
        UUID subjectId,
        String treeRoot,
        boolean verified) {
}
