package io.casehub.ledger.rest;

import java.util.Map;
import java.util.OptionalDouble;

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

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.rest.dto.CapabilityScoreResponse;
import io.casehub.ledger.rest.dto.TrustScoreResponse;

@Path("/api/v1/ledger/trust")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Trust Scores", description = "Bayesian Beta trust scores for actors")
public class TrustScoreResource {

    @Inject
    TrustScoreSource trustScoreSource;

    @GET
    @Path("/{actorId}")
    @Operation(summary = "Get all trust scores for an actor")
    public TrustScoreResponse getScores(
            @Parameter(description = "Actor identity") @PathParam("actorId") final String actorId) {

        final OptionalDouble global = trustScoreSource.globalScore(actorId);
        final Map<String, Double> capabilities = trustScoreSource.allCapabilityScores(actorId);
        final Map<String, Double> dimensions = trustScoreSource.allDimensionScores(actorId);
        return new TrustScoreResponse(actorId, global, capabilities, dimensions);
    }

    @GET
    @Path("/{actorId}/capability/{capabilityTag}")
    @Operation(summary = "Get capability-specific score and quality dimensions")
    public CapabilityScoreResponse getCapabilityScore(
            @Parameter(description = "Actor identity") @PathParam("actorId") final String actorId,
            @Parameter(description = "Capability tag") @PathParam("capabilityTag") final String capabilityTag) {

        final OptionalDouble score = trustScoreSource.capabilityScore(actorId, capabilityTag);
        final int decisions = trustScoreSource.decisionCount(actorId, capabilityTag);
        final Map<String, Double> quality = trustScoreSource.qualityScores(actorId, capabilityTag);
        return new CapabilityScoreResponse(actorId, capabilityTag, score, decisions, quality);
    }
}
