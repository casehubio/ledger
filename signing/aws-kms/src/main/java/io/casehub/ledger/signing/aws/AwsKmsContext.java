package io.casehub.ledger.signing.aws;

import java.security.PublicKey;

import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

/**
 * Cached context for a single AWS KMS key.
 *
 * <p>Held by {@code AwsKmsAgentSigner}'s context cache, keyed by actorId.
 *
 * @param keyArn AWS KMS key ARN
 * @param publicKey parsed EC public key
 * @param signingAlgorithm signing algorithm spec (derived from key spec at fetch time)
 */
public record AwsKmsContext(String keyArn, PublicKey publicKey, SigningAlgorithmSpec signingAlgorithm) {
}
