package io.casehub.ledger.signing.gcp;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.cloud.kms.v1.AsymmetricSignRequest;
import com.google.cloud.kms.v1.AsymmetricSignResponse;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.cloud.kms.v1.CryptoKeyVersionName;
import com.google.cloud.kms.v1.Digest;
import com.google.protobuf.ByteString;

/**
 * Pure Java client for GCP Cloud KMS signing.
 *
 * <p>The private key never leaves GCP Cloud KMS. Only the public key is fetched for storage on
 * ledger entries (needed by verification infrastructure). Signing happens via Cloud KMS API call.
 *
 * <p><strong>Auth:</strong> Uses Application Default Credentials
 * ({@code GOOGLE_APPLICATION_CREDENTIALS} or GCE metadata).
 *
 * <p><strong>Algorithm support:</strong> Only EC key algorithms are supported
 * ({@code EC_SIGN_P256_SHA256}, {@code EC_SIGN_P384_SHA384}). RSA and secp256k1 key algorithms
 * are rejected at {@code fetchPublicKey()} time (returns {@code null}, cached as absent).
 *
 * <p><strong>Digest is curve-dependent:</strong> The adapter reads
 * {@code CryptoKeyVersion.algorithm} and selects the matching digest:
 * <ul>
 *   <li>{@code EC_SIGN_P256_SHA256} → SHA-256 digest</li>
 *   <li>{@code EC_SIGN_P384_SHA384} → SHA-384 digest</li>
 * </ul>
 *
 * <p><strong>No casehub-ledger dependency.</strong> Usable from any framework or plain {@code main()}.
 */
public class GcpKmsSigningClient {

    private static final Logger LOG = Logger.getLogger(GcpKmsSigningClient.class.getName());

    private final GcpKmsClientWrapper kms;

    public GcpKmsSigningClient(final GcpKmsSigningConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.kms = new DefaultGcpKmsClientWrapper();
    }

    // Visible for testing — allows injecting a mocked wrapper
    public GcpKmsSigningClient(final GcpKmsSigningConfig config, final GcpKmsClientWrapper kmsClient) {
        Objects.requireNonNull(config, "config must not be null");
        this.kms = kmsClient;
    }

    /**
     * Exposes the wrapper for algorithm fetching by the Quarkus adapter.
     *
     * <p>Public — the Quarkus adapter is in a different package.
     */
    public GcpKmsClientWrapper getWrapper() {
        return kms;
    }

    /**
     * Fetches the public key from GCP Cloud KMS.
     *
     * <p>Calls {@code getPublicKey()} and parses the PEM-encoded public key.
     * Validates that the key algorithm is EC — only P-256 and P-384 are supported.
     * RSA and secp256k1 key algorithms are rejected (logged at ERROR, return {@code null}).
     *
     * @param versionName CryptoKeyVersion resource name
     * @return the EC public key, or {@code null} if the key is not found or is not an EC key
     */
    public PublicKey fetchPublicKey(final String versionName) {
        try {
            final com.google.cloud.kms.v1.PublicKey pubKeyResp = kms.getPublicKey(versionName);

            // Validate key algorithm — only EC is supported
            final CryptoKeyVersion.CryptoKeyVersionAlgorithm algorithm = pubKeyResp.getAlgorithm();
            if (!isECAlgorithm(algorithm)) {
                LOG.log(Level.SEVERE, "Key " + versionName + " is " + algorithm
                        + " but this adapter requires EC (EC_SIGN_P256_SHA256 or EC_SIGN_P384_SHA384)");
                return null;
            }

            // Parse PEM-encoded EC public key
            final String pem = pubKeyResp.getPem();
            final byte[] derBytes = parsePem(pem);
            final X509EncodedKeySpec spec = new X509EncodedKeySpec(derBytes);
            return KeyFactory.getInstance("EC").generatePublic(spec);
        } catch (final Exception e) {
            // NOT_FOUND, invalid algorithm → return null (cached as absent)
            // Other errors → propagate
            if (e.getMessage() != null && e.getMessage().contains("NOT_FOUND")) {
                LOG.log(Level.FINE, "Key version not found in GCP Cloud KMS: " + versionName);
                return null;
            }
            throw new RuntimeException("Failed to fetch public key from GCP Cloud KMS for key " + versionName, e);
        }
    }

    /**
     * Signs data via GCP Cloud KMS.
     *
     * <p>Reads the {@code CryptoKeyVersion.algorithm}, computes the appropriate digest
     * (P-256 → SHA-256, P-384 → SHA-384), and calls {@code asymmetricSign()}.
     * Returns DER-encoded signature bytes.
     *
     * @param versionName CryptoKeyVersion resource name
     * @param data        data to sign
     * @return DER-encoded ECDSA signature bytes
     * @throws RuntimeException on Cloud KMS API errors
     */
    public byte[] sign(final String versionName, final byte[] data) {
        try {
            // Fetch key version to determine algorithm
            final CryptoKeyVersion keyVersion = kms.getCryptoKeyVersion(versionName);
            final CryptoKeyVersion.CryptoKeyVersionAlgorithm algorithm = keyVersion.getAlgorithm();

            // Compute digest based on algorithm
            final byte[] digest = computeDigest(data, algorithm);

            // Build Digest proto
            final Digest digestProto = buildDigest(digest, algorithm);

            // Sign
            final AsymmetricSignRequest signReq = AsymmetricSignRequest.newBuilder()
                    .setName(versionName)
                    .setDigest(digestProto)
                    .build();
            final AsymmetricSignResponse signResp = kms.asymmetricSign(signReq);
            return signResp.getSignature().toByteArray();
        } catch (final Exception e) {
            throw new RuntimeException("GCP Cloud KMS signing failed for key " + versionName, e);
        }
    }

    private static boolean isECAlgorithm(final CryptoKeyVersion.CryptoKeyVersionAlgorithm algorithm) {
        return algorithm == CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256
                || algorithm == CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_P384_SHA384;
    }

    private static byte[] computeDigest(final byte[] data,
            final CryptoKeyVersion.CryptoKeyVersionAlgorithm algorithm) throws Exception {
        final String digestAlgo = switch (algorithm) {
            case EC_SIGN_P256_SHA256 -> "SHA-256";
            case EC_SIGN_P384_SHA384 -> "SHA-384";
            default -> throw new IllegalArgumentException(
                    "Unsupported algorithm for signing: " + algorithm);
        };
        return MessageDigest.getInstance(digestAlgo).digest(data);
    }

    private static Digest buildDigest(final byte[] digest,
            final CryptoKeyVersion.CryptoKeyVersionAlgorithm algorithm) {
        return switch (algorithm) {
            case EC_SIGN_P256_SHA256 -> Digest.newBuilder()
                    .setSha256(ByteString.copyFrom(digest))
                    .build();
            case EC_SIGN_P384_SHA384 -> Digest.newBuilder()
                    .setSha384(ByteString.copyFrom(digest))
                    .build();
            default -> throw new IllegalArgumentException(
                    "Unsupported algorithm for digest: " + algorithm);
        };
    }

    /**
     * Strips PEM headers and base64-decodes the body.
     *
     * <p>Inline implementation — no dependency on {@code LedgerPemUtil} (pure Java module
     * has no casehub-ledger dependency).
     */
    private static byte[] parsePem(final String pem) {
        final String body = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
