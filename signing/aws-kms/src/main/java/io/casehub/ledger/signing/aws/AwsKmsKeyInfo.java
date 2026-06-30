package io.casehub.ledger.signing.aws;

import java.security.PublicKey;

import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

/**
 * AWS KMS key information fetched from {@code getPublicKey()}.
 *
 * <p>Returned by {@link AwsKmsSigningClient#fetchPublicKey(String)}.
 * Holds both the parsed public key and the signing algorithm spec derived from the key spec.
 *
 * @param publicKey parsed EC public key
 * @param signingAlgorithm signing algorithm spec (P-256 → ECDSA_SHA_256, etc.)
 */
public record AwsKmsKeyInfo(PublicKey publicKey, SigningAlgorithmSpec signingAlgorithm) {
}
