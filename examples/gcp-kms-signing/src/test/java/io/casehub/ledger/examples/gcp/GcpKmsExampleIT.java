package io.casehub.ledger.examples.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.core.signing.AgentSignature;
import io.casehub.ledger.core.signing.AgentSigner;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Demonstrates GCP Cloud KMS signing module integration.
 * Production deployments configure real GCP credentials and Cloud KMS keys.
 * This test demonstrates configuration only — full signing requires real GCP infrastructure.
 */
@QuarkusTest
class GcpKmsExampleIT {

    @Inject
    AgentSigner agentSigner;

    @Test
    void returnsEmpty_forUnmappedActor() {
        final Optional<AgentSignature> result = agentSigner.sign("unmapped-actor", new byte[]{1});
        assertThat(result).isEmpty();
    }
}
