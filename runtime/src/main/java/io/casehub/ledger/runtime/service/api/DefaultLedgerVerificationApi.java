package io.casehub.ledger.runtime.service.api;

import io.casehub.ledger.api.spi.LedgerVerificationApi;
import io.casehub.ledger.api.view.InclusionProofView;
import io.casehub.ledger.api.view.VerificationView;
import io.casehub.ledger.core.merkle.InclusionProof;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@DefaultBean
@ApplicationScoped
public class DefaultLedgerVerificationApi implements LedgerVerificationApi {

    @Inject LedgerVerificationService verificationService;

    @Override
    public VerificationView verify(final UUID subjectId, final String tenancyId) {
        final String tid = DefaultLedgerEntryApi.defaultTenancyId(tenancyId);
        final boolean verified = verificationService.verify(subjectId, tid);
        final String treeRoot = verified
                ? verificationService.treeRoot(subjectId, tid) : null;
        return new VerificationView(subjectId, treeRoot, verified);
    }

    @Override
    public InclusionProofView inclusionProof(final UUID entryId,
                                              final String tenancyId) {
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
