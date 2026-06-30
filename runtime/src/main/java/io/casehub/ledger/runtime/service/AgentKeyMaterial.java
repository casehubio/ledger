package io.casehub.ledger.runtime.service;

import java.util.Objects;

/**
 * Key material for agent bilateral signing — public key and its reference.
 *
 * <p>Returned by {@link AgentSigner#keyMaterial(String)} for key-only retrieval
 * without triggering a signing operation (avoiding paid cloud KMS API calls).
 *
 * @param publicKey X.509 SubjectPublicKeyInfo DER-encoded public key
 * @param keyRef    Base64URL-encoded SHA-256 hash of {@code publicKey}
 */
public record AgentKeyMaterial(byte[] publicKey, String keyRef) {

    public AgentKeyMaterial {
        Objects.requireNonNull(keyRef, "keyRef must not be null");
        publicKey = publicKey.clone();
    }
}
