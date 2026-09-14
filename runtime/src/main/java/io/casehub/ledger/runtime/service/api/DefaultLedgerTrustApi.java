package io.casehub.ledger.runtime.service.api;

import io.casehub.ledger.api.spi.LedgerTrustApi;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.api.view.CapabilityScoreView;
import io.casehub.ledger.api.view.TrustRoutingProfileView;
import io.casehub.ledger.api.view.TrustScoreView;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@DefaultBean
@ApplicationScoped
public class DefaultLedgerTrustApi implements LedgerTrustApi {

    @Inject TrustScoreSource trustScoreSource;

    @Override
    public TrustScoreView trustScore(final String actorId) {
        return new TrustScoreView(
                actorId,
                trustScoreSource.globalScore(actorId),
                trustScoreSource.allCapabilityScores(actorId),
                trustScoreSource.allDimensionScores(actorId));
    }

    @Override
    public CapabilityScoreView capabilityScore(final String actorId,
                                                final String capabilityTag) {
        return new CapabilityScoreView(
                actorId, capabilityTag,
                trustScoreSource.capabilityScore(actorId, capabilityTag),
                trustScoreSource.decisionCount(actorId, capabilityTag),
                trustScoreSource.qualityScores(actorId, capabilityTag));
    }

    @Override
    public TrustRoutingProfileView routingProfile(final String actorId,
                                                    final String capabilityTag) {
        return new TrustRoutingProfileView(
                actorId, capabilityTag,
                trustScoreSource.globalScore(actorId),
                trustScoreSource.capabilityScore(actorId, capabilityTag),
                trustScoreSource.decisionCount(actorId, capabilityTag),
                trustScoreSource.qualityScores(actorId, capabilityTag));
    }
}
