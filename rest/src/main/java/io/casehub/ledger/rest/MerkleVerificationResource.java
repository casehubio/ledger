package io.casehub.ledger.rest;

import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.casehub.ledger.rest.dto.InclusionProofResponse;
import io.casehub.ledger.rest.dto.LedgerDtoMapper;
import io.casehub.ledger.rest.dto.VerificationResponse;
import io.casehub.ledger.runtime.service.LedgerVerificationService;

@Path("/api/v1/ledger/verify")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Merkle Verification", description = "Tamper-evidence verification via Merkle Mountain Range")
public class MerkleVerificationResource {

    @Inject
    LedgerVerificationService verificationService;

    @GET
    @Operation(summary = "Verify integrity of all entries for a subject")
    public VerificationResponse verify(
            @Parameter(description = "Subject aggregate UUID", required = true) @QueryParam("subjectId") final UUID subjectId,
            @Parameter(description = "Tenant scope") @QueryParam("tenancyId") final String tenancyId) {

        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId query parameter is required");
        }
        final String tid = LedgerRestUtil.requireTenancyId(tenancyId);
        final boolean verified = verificationService.verify(subjectId, tid);
        final String treeRoot = verified ? verificationService.treeRoot(subjectId, tid) : null;
        return new VerificationResponse(subjectId, treeRoot, verified);
    }

    @GET
    @Path("/entries/{entryId}/proof")
    @Operation(summary = "Get Merkle inclusion proof for a single entry")
    public InclusionProofResponse inclusionProof(
            @Parameter(description = "Entry UUID") @PathParam("entryId") final UUID entryId,
            @Parameter(description = "Tenant scope") @QueryParam("tenancyId") final String tenancyId) {

        final String tid = LedgerRestUtil.requireTenancyId(tenancyId);
        return LedgerDtoMapper.toResponse(verificationService.inclusionProof(entryId, tid));
    }
}
