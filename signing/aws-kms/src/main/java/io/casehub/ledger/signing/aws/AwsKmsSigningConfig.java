package io.casehub.ledger.signing.aws;

import java.util.Map;

/**
 * Configuration for AWS KMS signing client.
 *
 * <p>No framework dependencies — plain Java record.
 *
 * @param region AWS region (e.g., "us-east-1")
 * @param keyMapping actorId → KMS key ARN
 */
public record AwsKmsSigningConfig(
        String region,
        Map<String, String> keyMapping) {
}
