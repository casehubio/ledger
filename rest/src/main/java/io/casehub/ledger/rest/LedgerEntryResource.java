package io.casehub.ledger.rest;

import java.time.Instant;
import java.util.List;
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

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.rest.dto.LedgerDtoMapper;
import io.casehub.ledger.rest.dto.LedgerEntryResponse;

@Path("/api/v1/ledger/entries")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Ledger Entries", description = "Query audit ledger entries")
public class LedgerEntryResource {

    @Inject
    LedgerEntryRepository repository;

    @GET
    @Operation(summary = "Query entries by subject or actor")
    public List<LedgerEntryResponse> queryEntries(
            @Parameter(description = "Subject aggregate UUID") @QueryParam("subjectId") final UUID subjectId,
            @Parameter(description = "Actor identity") @QueryParam("actorId") final String actorId,
            @Parameter(description = "Tenant scope") @QueryParam("tenancyId") final String tenancyId,
            @Parameter(description = "Range start (inclusive)") @QueryParam("from") final Instant from,
            @Parameter(description = "Range end (inclusive)") @QueryParam("to") final Instant to) {

        final String tid = LedgerRestUtil.requireTenancyId(tenancyId);

        if (subjectId != null) {
            final List<? extends LedgerEntry> entries;
            if (from != null && to != null) {
                entries = repository.findBySubjectIdAndTimeRange(subjectId, from, to, tid);
            } else {
                entries = repository.findBySubjectId(subjectId, tid);
            }
            return LedgerDtoMapper.toResponseList(entries);
        }

        if (actorId != null) {
            final Instant start = from != null ? from : Instant.EPOCH;
            final Instant end = to != null ? to : Instant.now();
            return LedgerDtoMapper.toResponseList(repository.findByActorId(actorId, start, end, tid));
        }

        throw new IllegalArgumentException("Either subjectId or actorId query parameter is required");
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a single entry by ID")
    public LedgerEntryResponse getEntry(
            @Parameter(description = "Entry UUID") @PathParam("id") final UUID id,
            @Parameter(description = "Tenant scope") @QueryParam("tenancyId") final String tenancyId) {

        final String tid = LedgerRestUtil.requireTenancyId(tenancyId);
        return repository.findEntryById(id, tid)
                .map(LedgerDtoMapper::toResponse)
                .orElseThrow(() -> new LedgerNotFoundException("Entry not found: " + id));
    }

    @GET
    @Path("/{id}/caused-by")
    @Operation(summary = "Get entries causally triggered by this entry")
    public List<LedgerEntryResponse> getCausedBy(
            @Parameter(description = "Entry UUID") @PathParam("id") final UUID id,
            @Parameter(description = "Tenant scope") @QueryParam("tenancyId") final String tenancyId) {

        final String tid = LedgerRestUtil.requireTenancyId(tenancyId);
        return LedgerDtoMapper.toResponseList(repository.findCausedBy(id, tid));
    }
}
