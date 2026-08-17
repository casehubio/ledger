package io.casehub.ledger.graphql.dto;

import java.util.UUID;
import org.eclipse.microprofile.graphql.Type;

@Type("MerkleVerification")
public record MerkleVerificationType(
        UUID subjectId,
        String treeRoot,
        boolean verified) {
}
