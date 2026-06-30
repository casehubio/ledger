package io.casehub.ledger.signing.aws.quarkus;

import java.util.Map;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Quarkus config mapping for AWS KMS signing.
 *
 * <p>Bridges Quarkus config properties to the pure Java {@code AwsKmsSigningConfig} POJO.
 *
 * <p><strong>Activation:</strong>
 * <pre>
 * quarkus.arc.selected-alternatives=io.casehub.ledger.signing.aws.quarkus.AwsKmsAgentSigner
 * </pre>
 */
@ConfigMapping(prefix = "casehub.ledger.aws-kms")
public interface AwsKmsConfig {

    /**
     * AWS region (e.g., "us-east-1").
     */
    @WithDefault("us-east-1")
    String region();

    /**
     * Actor ID → KMS key ARN mapping.
     */
    Map<String, String> keyMapping();

    /**
     * Context cache refresh interval.
     * Default: 5 minutes.
     */
    @WithDefault("5m")
    String refreshInterval();
}
