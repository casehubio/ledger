package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.view.CapabilityScoreView;
import io.casehub.ledger.api.view.TrustRoutingProfileView;
import io.casehub.ledger.api.view.TrustScoreView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformQuery;

@McpDomain("ledger/trust")
public interface LedgerTrustApi {

    @PlatformQuery("Global trust score for an actor — aggregate across all capabilities")
    TrustScoreView trustScore(@PathParam String actorId);

    @PlatformQuery("Capability-scoped trust score with quality dimensions")
    CapabilityScoreView capabilityScore(@PathParam String actorId,
                                         @PathParam String capabilityTag);

    @PlatformQuery("Composite trust routing profile — global + capability in one call")
    TrustRoutingProfileView routingProfile(@PathParam String actorId,
                                            @PathParam String capabilityTag);
}
