package io.casehub.ledger.signing.azure.quarkus;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.arc.DefaultBean;

/**
 * Test-only {@link CurrentPrincipal} for the Azure Key Vault Quarkus adapter tests.
 */
@DefaultBean
@ApplicationScoped
class TestCurrentPrincipal implements CurrentPrincipal {

    @Override
    public String actorId() {
        return "test-principal";
    }

    @Override
    public Set<String> groups() {
        return Set.of();
    }

    @Override
    public String tenancyId() {
        return TenancyConstants.DEFAULT_TENANT_ID;
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return false;
    }
}
