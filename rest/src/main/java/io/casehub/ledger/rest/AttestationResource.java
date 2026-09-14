package io.casehub.ledger.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.rest.dto.AttestationResponse;
import io.casehub.ledger.rest.dto.CreateAttestationRequest;
import io.casehub.ledger.rest.dto.LedgerDtoMapper;
import io.casehub.platform.api.identity.ActorType;

@Path("/api/v1/ledger/entries/{entryId}/attestations")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Attestations", description = "Peer attestations on ledger entries")
public class AttestationResource {

    @Inject
    LedgerEntryRepository repository;

    @GET
    @Operation(summary = "List attestations for a ledger entry")
    public List<AttestationResponse> listAttestations(
            @Parameter(description = "Entry UUID") @PathParam("entryId") final UUID entryId,
            @Parameter(description = "Tenant scope") @QueryParam("tenancyId") final String tenancyId,
            @Parameter(description = "Filter by capability tag") @QueryParam("capabilityTag") final String capabilityTag) {

        final String tid = LedgerRestUtil.requireTenancyId(tenancyId);

        final List<LedgerAttestation> attestations;
        if (capabilityTag != null) {
            attestations = repository.findAttestationsByEntryIdAndCapabilityTag(entryId, capabilityTag, tid);
        } else {
            attestations = repository.findAttestationsByEntryId(entryId, tid);
        }
        return LedgerDtoMapper.toAttestationList(attestations);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create an attestation on a ledger entry")
    public Response createAttestation(
            @Parameter(description = "Entry UUID") @PathParam("entryId") final UUID entryId,
            @Parameter(description = "Tenant scope") @QueryParam("tenancyId") final String tenancyId,
            final CreateAttestationRequest request) {

        final String tid = LedgerRestUtil.requireTenancyId(tenancyId);

        final var entry = repository.findEntryById(entryId, tid)
                .orElseThrow(() -> new LedgerNotFoundException("Entry not found: " + entryId));

        if (request.attestorId() == null || request.attestorType() == null || request.verdict() == null) {
            throw new IllegalArgumentException("attestorId, attestorType, and verdict are required");
        }

        final var attestation = new LedgerAttestation();
        attestation.id = UUID.randomUUID();
        attestation.ledgerEntryId = entryId;
        attestation.subjectId = entry.subjectId;
        attestation.attestorId = request.attestorId();
        attestation.attestorType = ActorType.valueOf(request.attestorType());
        attestation.attestorRole = request.attestorRole();
        attestation.verdict = AttestationVerdict.valueOf(request.verdict());
        attestation.evidence = request.evidence();
        attestation.confidence = request.confidence();
        attestation.capabilityTag = request.capabilityTag();
        attestation.trustDimension = request.trustDimension();
        attestation.dimensionScore = request.dimensionScore();
        attestation.occurredAt = Instant.now();

        final LedgerAttestation saved = repository.saveAttestation(attestation, tid);
        return Response.status(Response.Status.CREATED)
                .entity(LedgerDtoMapper.toResponse(saved))
                .build();
    }
}
