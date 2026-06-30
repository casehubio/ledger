package io.casehub.ledger.examples.aws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Demonstrates AWS KMS signing module integration.
 * Production deployments configure real AWS credentials and KMS keys.
 * This test demonstrates configuration only — full signing requires real AWS infrastructure.
 */
@QuarkusTest
class AwsKmsExampleIT {

    @Inject
    AgentSigner agentSigner;

    @Test
    void returnsEmpty_forUnmappedActor() {
        final Optional<AgentSignature> result = agentSigner.sign("unmapped-actor", new byte[]{1});
        assertThat(result).isEmpty();
    }
}
