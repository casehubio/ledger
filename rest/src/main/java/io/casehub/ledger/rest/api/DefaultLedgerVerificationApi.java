package io.casehub.ledger.rest.api;

import io.casehub.ledger.api.view.InclusionProofView;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.ledger.api.view.VerificationView;
import io.casehub.ledger.core.merkle.InclusionProof;
import io.casehub.ledger.runtime.service.LedgerVerificationService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@McpDomain(value = "ledger/verification", basePath = "/api/v1/ledger")
@ApplicationScoped
public class DefaultLedgerVerificationApi {

    @Inject LedgerVerificationService verificationService;

    @PlatformQuery("Verify Merkle tree integrity for all entries of a subject")
    public VerificationView verify(UUID subjectId, @ContextParam("tenancyId") String tenancyId) {
        final String tid = DefaultLedgerEntryApi.defaultTenancyId(tenancyId);
        final boolean verified = verificationService.verify(subjectId, tid);
        final String treeRoot = verified
                ? verificationService.treeRoot(subjectId, tid) : null;
        return new VerificationView(subjectId, treeRoot, verified);
    }

    @PlatformQuery("Get Merkle inclusion proof for a single entry")
    public InclusionProofView inclusionProof(@PathParam UUID entryId,
                                              @ContextParam("tenancyId") String tenancyId) {
        final String tid = DefaultLedgerEntryApi.defaultTenancyId(tenancyId);
        final InclusionProof proof = verificationService.inclusionProof(entryId, tid);
        final var steps = proof.siblings().stream()
                .map(s -> new InclusionProofView.ProofStepView(
                        s.hash(), s.side().name()))
                .toList();
        return new InclusionProofView(
                proof.entryId(), proof.entryIndex(), proof.treeSize(),
                proof.leafHash(), steps, proof.treeRoot());
    }
}
