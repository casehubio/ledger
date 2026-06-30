package io.casehub.ledger.runtime.service;

import java.security.Key;
import java.security.interfaces.ECKey;

/**
 * Signature algorithm mapping utility.
 * Maps EC curve parameters to their JCA signature algorithm names.
 * Non-EC algorithms pass through unchanged.
 *
 * <p>Package-private — shared by {@link AgentCryptographicVerifier},
 * {@link AgentSignature}, and {@link LedgerMerklePublisher}.
 */
final class SignatureAlgorithms {

    private SignatureAlgorithms() {}

    /**
     * Derives the JCA {@code Signature} algorithm name from a key.
     * For EC keys, maps curve order to the appropriate ECDSA variant.
     * For all other algorithms, returns {@code key.getAlgorithm()} unchanged.
     *
     * @param key the signing or verification key
     * @return JCA signature algorithm name (e.g., "SHA256withECDSA", "Ed25519")
     * @throws IllegalArgumentException if the EC curve order is not supported
     */
    static String signatureAlgorithm(final Key key) {
        if (!"EC".equals(key.getAlgorithm())) {
            return key.getAlgorithm();
        }
        final ECKey ec = (ECKey) key;
        return switch (ec.getParams().getOrder().bitLength()) {
            case 256 -> "SHA256withECDSA";
            case 384 -> "SHA384withECDSA";
            case 521 -> "SHA512withECDSA";
            default -> throw new IllegalArgumentException(
                    "Unsupported EC curve order: " + ec.getParams().getOrder().bitLength());
        };
    }
}
