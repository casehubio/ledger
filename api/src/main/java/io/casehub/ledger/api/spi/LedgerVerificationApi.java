package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.view.InclusionProofView;
import io.casehub.ledger.api.view.VerificationView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.util.UUID;

@McpDomain("ledger/verification")
public interface LedgerVerificationApi {

    @PlatformQuery("Verify Merkle tree integrity for all entries of a subject")
    VerificationView verify(UUID subjectId, String tenancyId);

    @PlatformQuery("Get Merkle inclusion proof for a single entry")
    InclusionProofView inclusionProof(@PathParam UUID entryId, String tenancyId);
}
