package io.casehub.ledger.api.view;

import java.util.List;
import java.util.UUID;

public record InclusionProofView(
        UUID entryId,
        int entryIndex,
        int treeSize,
        String leafHash,
        List<ProofStepView> siblings,
        String treeRoot) {

    public record ProofStepView(String hash, String side) {
    }
}
