package io.casehub.ledger.rest.dto;

import java.util.List;
import java.util.UUID;

public record InclusionProofResponse(
        UUID entryId,
        int entryIndex,
        int treeSize,
        String leafHash,
        List<ProofStepResponse> siblings,
        String treeRoot) {

    public record ProofStepResponse(String hash, String side) {
    }
}
