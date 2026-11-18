package io.casehub.ledger.runtime;

import io.casehub.ledger.api.spi.AttestorCredibilityPolicy;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.core.federation.NoOpTrustBootstrapSource;
import io.casehub.ledger.core.federation.TrustBootstrapSource;
import io.casehub.ledger.core.signing.AgentEntrySigner;
import io.casehub.ledger.core.signing.AgentSigner;
import io.casehub.ledger.core.trust.AllAttestationsGlobalStrategy;
import io.casehub.ledger.core.trust.AttestationAggregator;
import io.casehub.ledger.core.trust.DecayFunction;
import io.casehub.ledger.core.trust.ExponentialDecayFunction;
import io.casehub.ledger.core.trust.GlobalScoreStrategy;
import io.casehub.ledger.core.trust.NoOpAttestorCredibilityPolicy;
import io.casehub.ledger.core.trust.TrustGateService;
import io.casehub.ledger.core.trust.RelevanceComposer;
import io.casehub.ledger.core.trust.TrustScoreCalculator;
import io.casehub.ledger.runtime.config.LedgerConfig;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class LedgerCoreProducer {

    @Produces
    @DefaultBean
    public DecayFunction exponentialDecayFunction(final LedgerConfig config) {
        return new ExponentialDecayFunction(
                config.trustScore().decayHalfLifeDays(),
                config.decay().flaggedPersistenceMultiplier());
    }

    @Produces
    @DefaultBean
    public GlobalScoreStrategy allAttestationsGlobalStrategy() {
        return new AllAttestationsGlobalStrategy();
    }

    @Produces
    @DefaultBean
    public TrustScoreCalculator trustScoreCalculator(final DecayFunction decayFunction,
                                                     final GlobalScoreStrategy globalStrategy,
                                                     final AttestorCredibilityPolicy credibilityPolicy,
                                                     final RelevanceComposer relevanceComposer) {
        return new TrustScoreCalculator(decayFunction, globalStrategy, credibilityPolicy, relevanceComposer);
    }

    @Produces
    @DefaultBean
    public RelevanceComposer relevanceComposer(
            @Any Instance<io.casehub.ledger.api.spi.AttestationRelevanceFactor> factors,
            LedgerConfig config) {
        return new RelevanceComposer(factors.stream().toList(),
                config.trustScore().relevance().minWeight());
    }

    @Produces
    @DefaultBean
    public TrustGateService trustGateService(final TrustScoreSource source) {
        return new TrustGateService(source);
    }

    @Produces
    @DefaultBean
    public AttestationAggregator attestationAggregator() {
        return new AttestationAggregator();
    }

    @Produces
    @DefaultBean
    public AgentEntrySigner agentEntrySigner(final AgentSigner signer) {
        return new AgentEntrySigner(signer);
    }

    @Produces
    @DefaultBean
    public AttestorCredibilityPolicy noOpAttestorCredibilityPolicy() {
        return new NoOpAttestorCredibilityPolicy();
    }

    @Produces
    @DefaultBean
    public TrustBootstrapSource noOpTrustBootstrapSource() {
        return new NoOpTrustBootstrapSource();
    }

}
