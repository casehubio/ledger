package io.casehub.ledger.runtime.service;

import java.util.Optional;

/**
 * SPI: performs (or delegates) the signing of a {@link io.casehub.ledger.runtime.model.LedgerEntry}
 * on behalf of the given actorId, returning the complete signature result.
 *
 * <p>Return {@link Optional#empty()} for actors that do not participate in bilateral signing —
 * their entries will be persisted unsigned.
 *
 * <p><strong>Error handling:</strong> throw {@link RuntimeException} for transient failures
 * (network, auth). The enricher swallows it and leaves the entry unsigned. Do NOT return
 * empty to signal an error — reserve empty for "actor not configured".
 *
 * <p><strong>Thread safety:</strong> implementations must be safe for concurrent calls.
 *
 * <p><strong>Algorithm transparency:</strong> never hardcode a cryptographic algorithm string.
 * See protocol PP-20260523-e7b577.
 *
 * <p>This interface has one abstract method and supports lambda construction in tests
 * (SAM interface). {@code @FunctionalInterface} is intentionally absent to allow
 * future {@code default} methods.
 */
public interface AgentSigner {

    /**
     * @param actorId the actor identity (e.g. {@code "claude:reviewer@v1"})
     * @param data    canonical bytes to sign ({@link io.casehub.ledger.runtime.model.LedgerEntry#canonicalBytes()})
     * @return signed result, or empty if this actor does not sign entries
     */
    Optional<AgentSignature> sign(String actorId, byte[] data);

    /**
     * Retrieve key material without signing. Avoids wasted cloud KMS sign calls in
     * {@link AgentEntrySigner#prepareKey(io.casehub.ledger.runtime.model.LedgerEntry)}.
     *
     * <p>Default implementation calls {@link #sign(String, byte[])} with dummy data
     * and extracts the key material. Concrete implementations that cache public keys
     * (e.g. {@link AbstractCachingAgentSigner}) override this to avoid triggering
     * {@code performSign()}.
     *
     * @param actorId the actor identity
     * @return key material (public key + keyRef), or empty if this actor does not sign entries
     */
    default Optional<AgentKeyMaterial> keyMaterial(String actorId) {
        return sign(actorId, new byte[0])
                .map(sig -> new AgentKeyMaterial(sig.publicKey(), sig.keyRef()));
    }
}
