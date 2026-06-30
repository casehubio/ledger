package io.casehub.ledger.examples.gcp;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.casehub.platform.api.identity.CurrentPrincipal;

@ApplicationScoped
class TestProducers {

    @Produces
    @ApplicationScoped
    CurrentPrincipal mockCurrentPrincipal() {
        return new CurrentPrincipal() {
            @Override
            public String actorId() {
                return "test-actor";
            }

            @Override
            public String tenancyId() {
                return "default";
            }

            @Override
            public Set<String> groups() {
                return Set.of();
            }

            @Override
            public boolean isCrossTenantAdmin() {
                return false;
            }
        };
    }
}
