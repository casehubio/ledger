package io.casehub.ledger.signing.aws;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

import java.util.logging.Level;
import java.util.logging.Logger;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

/**
 * Pure Java client for AWS KMS signing.
 *
 * <p>The private key never leaves AWS KMS. Only the public key is fetched for storage on
 * ledger entries (needed by verification infrastructure). Signing happens via KMS API call.
 *
 * <p><strong>Auth:</strong> Uses the AWS default credential provider chain (env vars,
 * {@code ~/.aws/credentials}, instance metadata, ECS task role).
 *
 * <p><strong>Algorithm support:</strong> Only EC key specs are supported
 * ({@code ECC_NIST_P256}, {@code ECC_NIST_P384}, {@code ECC_NIST_P521}). RSA key specs
 * are rejected at {@code fetchPublicKey()} time (returns {@code null}, cached as absent).
 *
 * <p><strong>No casehub-ledger dependency.</strong> Usable from any framework or plain {@code main()}.
 */
public class AwsKmsSigningClient {

    private static final Logger LOG = Logger.getLogger(AwsKmsSigningClient.class.getName());

    private final KmsClient kms;

    public AwsKmsSigningClient(final AwsKmsSigningConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.kms = KmsClient.builder()
                .region(Region.of(config.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    // Visible for testing — allows injecting a mocked KmsClient
    public AwsKmsSigningClient(final AwsKmsSigningConfig config, final KmsClient kmsClient) {
        Objects.requireNonNull(config, "config must not be null");
        this.kms = kmsClient;
    }

    /**
     * Fetches the public key and signing algorithm from AWS KMS.
     *
     * <p>Calls {@code getPublicKey()} and parses the DER-encoded public key bytes.
     * Validates that the key spec is EC — only P-256, P-384, and P-521 are supported.
     * RSA key specs are rejected (logged at ERROR, return {@code null}).
     *
     * @param keyArn AWS KMS key ARN
     * @return key info (public key + signing algorithm), or {@code null} if the key is not found or is not an EC key
     */
    public AwsKmsKeyInfo fetchPublicKey(final String keyArn) {
        try {
            final GetPublicKeyRequest req = GetPublicKeyRequest.builder()
                    .keyId(keyArn)
                    .build();
            final GetPublicKeyResponse resp = kms.getPublicKey(req);

            // Validate key spec — only EC is supported
            final KeySpec keySpec = resp.keySpec();
            if (!isECKeySpec(keySpec)) {
                LOG.log(Level.SEVERE, "Key " + keyArn + " is " + keySpec
                        + " but this adapter requires EC (ECC_NIST_P256/P384/P521)");
                return null;
            }

            // Parse DER-encoded EC public key
            final byte[] derBytes = resp.publicKey().asByteArray();
            final X509EncodedKeySpec spec = new X509EncodedKeySpec(derBytes);
            final PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(spec);

            // Derive signing algorithm from key spec
            final SigningAlgorithmSpec signingAlgorithm = mapKeySpecToSigningAlgorithm(keySpec);

            return new AwsKmsKeyInfo(publicKey, signingAlgorithm);
        } catch (final NotFoundException e) {
            LOG.log(Level.FINE, "Key not found in AWS KMS: " + keyArn);
            return null;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to fetch public key from AWS KMS for key " + keyArn, e);
        }
    }

    /**
     * Signs data via AWS KMS.
     *
     * <p>Calls {@code sign()} with the provided signing algorithm spec.
     * Returns DER-encoded signature bytes.
     *
     * @param keyArn AWS KMS key ARN
     * @param data   data to sign
     * @param signingAlgorithm signing algorithm spec (from {@link AwsKmsKeyInfo})
     * @return DER-encoded ECDSA signature bytes
     * @throws RuntimeException on KMS API errors
     */
    public byte[] sign(final String keyArn, final byte[] data, final SigningAlgorithmSpec signingAlgorithm) {
        try {
            final SignRequest signReq = SignRequest.builder()
                    .keyId(keyArn)
                    .message(software.amazon.awssdk.core.SdkBytes.fromByteArray(data))
                    .signingAlgorithm(signingAlgorithm)
                    .build();
            final SignResponse signResp = kms.sign(signReq);
            return signResp.signature().asByteArray();
        } catch (final Exception e) {
            throw new RuntimeException("AWS KMS signing failed for key " + keyArn, e);
        }
    }

    private static boolean isECKeySpec(final KeySpec keySpec) {
        return keySpec == KeySpec.ECC_NIST_P256
                || keySpec == KeySpec.ECC_NIST_P384
                || keySpec == KeySpec.ECC_NIST_P521;
    }

    private static SigningAlgorithmSpec mapKeySpecToSigningAlgorithm(final KeySpec keySpec) {
        return switch (keySpec) {
            case ECC_NIST_P256 -> SigningAlgorithmSpec.ECDSA_SHA_256;
            case ECC_NIST_P384 -> SigningAlgorithmSpec.ECDSA_SHA_384;
            case ECC_NIST_P521 -> SigningAlgorithmSpec.ECDSA_SHA_512;
            default -> throw new IllegalArgumentException("Unsupported key spec for signing: " + keySpec);
        };
    }
}
