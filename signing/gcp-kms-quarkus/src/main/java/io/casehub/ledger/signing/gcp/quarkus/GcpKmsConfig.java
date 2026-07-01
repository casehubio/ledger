package io.casehub.ledger.signing.gcp.quarkus;

import java.util.Map;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Quarkus config mapping for GCP Cloud KMS signing.
 *
 * <p>Maps actor IDs to CryptoKeyVersion resource names for {@link GcpKmsAgentSigner}.
 *
 * <p>Example:
 * <pre>
 * casehub.ledger.gcp-kms.key-mapping.actor-1=projects/my-project/locations/us-central1/keyRings/my-ring/cryptoKeys/my-key/cryptoKeyVersions/1
 * casehub.ledger.gcp-kms.refresh-interval=5m
 * </pre>
 */
@ConfigMapping(prefix = "casehub.ledger.gcp-kms")
public interface GcpKmsConfig {

    /**
     * Actor ID → CryptoKeyVersion resource name.
     *
     * @return key mapping
     */
    Map<String, String> keyMapping();

    /**
     * Cache refresh interval.
     *
     * @return refresh interval (default 5m)
     */
    @WithDefault("5m")
    String refreshInterval();
}
