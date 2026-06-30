package io.casehub.ledger.examples.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Demonstrates Azure Key Vault signing module integration.
 * Production deployments configure real Azure credentials and Key Vault keys.
 * This test demonstrates configuration only — full signing requires real Azure infrastructure.
 */
@QuarkusTest
class AzureKeyVaultExampleIT {

    @Inject
    AgentSigner agentSigner;

    @Test
    void returnsEmpty_forUnmappedActor() {
        final Optional<AgentSignature> result = agentSigner.sign("unmapped-actor", new byte[]{1});
        assertThat(result).isEmpty();
    }
}
