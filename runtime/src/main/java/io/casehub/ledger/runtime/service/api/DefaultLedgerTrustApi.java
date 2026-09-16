package io.casehub.ledger.runtime.service.api;

import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.ledger.api.view.CapabilityScoreView;
import io.casehub.ledger.api.view.TrustRoutingProfileView;
import io.casehub.ledger.api.view.TrustScoreView;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpDomain(value = "ledger/trust", basePath = "/api/v1/ledger/trust")
@ApplicationScoped
public class DefaultLedgerTrustApi {

    @Inject TrustScoreSource trustScoreSource;

    @PlatformQuery("Global trust score for an actor — aggregate across all capabilities")
    public TrustScoreView trustScore(@PathParam String actorId) {
        return new TrustScoreView(
                actorId,
                trustScoreSource.globalScore(actorId),
                trustScoreSource.allCapabilityScores(actorId),
                trustScoreSource.allDimensionScores(actorId));
    }

    @PlatformQuery("Capability-scoped trust score with quality dimensions")
    public CapabilityScoreView capabilityScore(@PathParam String actorId,
                                                @PathParam String capabilityTag) {
        return new CapabilityScoreView(
                actorId, capabilityTag,
                trustScoreSource.capabilityScore(actorId, capabilityTag),
                trustScoreSource.decisionCount(actorId, capabilityTag),
                trustScoreSource.qualityScores(actorId, capabilityTag));
    }

    @PlatformQuery("Composite trust routing profile — global + capability in one call")
    public TrustRoutingProfileView routingProfile(@PathParam String actorId,
                                                    @PathParam String capabilityTag) {
        return new TrustRoutingProfileView(
                actorId, capabilityTag,
                trustScoreSource.globalScore(actorId),
                trustScoreSource.capabilityScore(actorId, capabilityTag),
                trustScoreSource.decisionCount(actorId, capabilityTag),
                trustScoreSource.qualityScores(actorId, capabilityTag));
    }
}
