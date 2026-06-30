package io.casehub.ledger.signing.gcp;

import java.security.PublicKey;

import com.google.cloud.kms.v1.CryptoKeyVersion;

/**
 * Cached context for a GCP Cloud KMS signing key.
 *
 * <p>Holds the key version resource name, public key, and algorithm for digest selection.
 *
 * @param versionName CryptoKeyVersion resource name
 * @param publicKey   EC public key
 * @param algorithm   CryptoKeyVersion algorithm (e.g., {@code EC_SIGN_P256_SHA256})
 */
public record GcpKmsContext(String versionName, PublicKey publicKey,
        CryptoKeyVersion.CryptoKeyVersionAlgorithm algorithm) {
}
