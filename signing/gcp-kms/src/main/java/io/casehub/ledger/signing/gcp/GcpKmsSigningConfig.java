package io.casehub.ledger.signing.gcp;

import java.util.Map;

/**
 * Configuration for GCP Cloud KMS signing.
 *
 * <p>Plain Java record — no framework annotations. Usable from any context.
 *
 * @param keyMapping actorId → CryptoKeyVersion resource name
 *                   (e.g., {@code projects/my-project/locations/us-central1/keyRings/my-ring/cryptoKeys/my-key/cryptoKeyVersions/1})
 */
public record GcpKmsSigningConfig(Map<String, String> keyMapping) {
}
