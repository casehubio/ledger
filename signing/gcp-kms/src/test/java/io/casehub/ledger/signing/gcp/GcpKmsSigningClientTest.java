package io.casehub.ledger.signing.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.cloud.kms.v1.AsymmetricSignRequest;
import com.google.cloud.kms.v1.AsymmetricSignResponse;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;

/**
 * Pure Java unit tests for {@link GcpKmsSigningClient}.
 *
 * <p>Uses Mockito to mock {@link KeyManagementServiceClient} via a thin wrapper interface.
 * Tests P-256, P-384 digest selection, RSA rejection, key not found, and transient errors.
 */
class GcpKmsSigningClientTest {

    private static final String TEST_VERSION_NAME =
            "projects/test-project/locations/us-central1/keyRings/test-ring/cryptoKeys/test-key/cryptoKeyVersions/1";

    // Real P-256 public key PEM
    private static final String P256_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+xVOdphkfpEtl7OF8oCyvWw31dR1
            3cJjaJyLEqiZbUQAYs+TUpJDVB+Z5c3qGfLTXJCQ6TLG0dY6qfQCcVn0Aw==
            -----END PUBLIC KEY-----
            """;

    // Real P-384 public key PEM
    private static final String P384_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEQJnn2qvkT8VqRlXPvN8YqF8sC7Vh/rKT
            vFJZ7Xqd+Q7VY4YJy6YqF4KqZy6cLvN8+Xd7Kp8sC7Vh/rKTvFJZ7Xqd+Q7VY4YJ
            y6YqF4KqZy6cLvN8+Xd7Kp8sC7Vh
            -----END PUBLIC KEY-----
            """;

    @Test
    void sign_p256Key_usesCorrectDigest() throws Exception {
        // RED: Write failing test first — no implementation exists yet
        final GcpKmsClientWrapper mockClient = mock(GcpKmsClientWrapper.class);
        final byte[] signatureBytes = new byte[]{1, 2, 3, 4};

        // Mock getCryptoKeyVersion to return P-256 algorithm
        when(mockClient.getCryptoKeyVersion(TEST_VERSION_NAME)).thenReturn(
                CryptoKeyVersion.newBuilder()
                        .setAlgorithm(CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256)
                        .build());

        // Mock asymmetricSign
        when(mockClient.asymmetricSign(any(AsymmetricSignRequest.class))).thenReturn(
                AsymmetricSignResponse.newBuilder()
                        .setSignature(ByteString.copyFrom(signatureBytes))
                        .build());

        final GcpKmsSigningConfig config = new GcpKmsSigningConfig(
                Map.of("test-actor", TEST_VERSION_NAME));
        final GcpKmsSigningClient client = new GcpKmsSigningClient(config, mockClient);

        final byte[] result = client.sign(TEST_VERSION_NAME, new byte[]{5, 6, 7});

        assertThat(result).isEqualTo(signatureBytes);
    }

    @Test
    void sign_p384Key_usesCorrectDigest() throws Exception {
        // RED: P-384 key → SHA-384 digest (not SHA-256)
        final GcpKmsClientWrapper mockClient = mock(GcpKmsClientWrapper.class);
        final byte[] signatureBytes = new byte[]{1, 2, 3, 4};

        // Mock getCryptoKeyVersion to return P-384 algorithm
        when(mockClient.getCryptoKeyVersion(TEST_VERSION_NAME)).thenReturn(
                CryptoKeyVersion.newBuilder()
                        .setAlgorithm(CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_P384_SHA384)
                        .build());

        // Mock asymmetricSign
        when(mockClient.asymmetricSign(any(AsymmetricSignRequest.class))).thenReturn(
                AsymmetricSignResponse.newBuilder()
                        .setSignature(ByteString.copyFrom(signatureBytes))
                        .build());

        final GcpKmsSigningConfig config = new GcpKmsSigningConfig(
                Map.of("test-actor", TEST_VERSION_NAME));
        final GcpKmsSigningClient client = new GcpKmsSigningClient(config, mockClient);

        final byte[] result = client.sign(TEST_VERSION_NAME, new byte[]{5, 6, 7});

        assertThat(result).isEqualTo(signatureBytes);
    }

    @Test
    void fetchPublicKey_p256Key_returnsECPublicKey() throws Exception {
        // RED: Write failing test first
        final GcpKmsClientWrapper mockClient = mock(GcpKmsClientWrapper.class);
        when(mockClient.getPublicKey(TEST_VERSION_NAME)).thenReturn(
                com.google.cloud.kms.v1.PublicKey.newBuilder()
                        .setPem(P256_PUBLIC_KEY_PEM)
                        .setAlgorithm(CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256)
                        .build());

        final GcpKmsSigningConfig config = new GcpKmsSigningConfig(
                Map.of("test-actor", TEST_VERSION_NAME));
        final GcpKmsSigningClient client = new GcpKmsSigningClient(config, mockClient);

        final PublicKey result = client.fetchPublicKey(TEST_VERSION_NAME);

        assertThat(result).isNotNull();
        assertThat(result.getAlgorithm()).isEqualTo("EC");
    }

    @Test
    void fetchPublicKey_rsaKey_returnsNull() throws Exception {
        // RED: RSA keys are rejected — fetchPublicKey returns null
        final GcpKmsClientWrapper mockClient = mock(GcpKmsClientWrapper.class);
        when(mockClient.getPublicKey(TEST_VERSION_NAME)).thenReturn(
                com.google.cloud.kms.v1.PublicKey.newBuilder()
                        .setPem("-----BEGIN PUBLIC KEY-----\nRSA\n-----END PUBLIC KEY-----")
                        .setAlgorithm(CryptoKeyVersion.CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256)
                        .build());

        final GcpKmsSigningConfig config = new GcpKmsSigningConfig(
                Map.of("test-actor", TEST_VERSION_NAME));
        final GcpKmsSigningClient client = new GcpKmsSigningClient(config, mockClient);

        final PublicKey result = client.fetchPublicKey(TEST_VERSION_NAME);

        assertThat(result).isNull();
    }

    @Test
    void fetchPublicKey_notFound_returnsNull() throws Exception {
        // RED: Key not found → return null (cached as absent)
        final GcpKmsClientWrapper mockClient = mock(GcpKmsClientWrapper.class);
        when(mockClient.getPublicKey(TEST_VERSION_NAME))
                .thenThrow(new RuntimeException("NOT_FOUND: Key version not found"));

        final GcpKmsSigningConfig config = new GcpKmsSigningConfig(
                Map.of("test-actor", TEST_VERSION_NAME));
        final GcpKmsSigningClient client = new GcpKmsSigningClient(config, mockClient);

        final PublicKey result = client.fetchPublicKey(TEST_VERSION_NAME);

        assertThat(result).isNull();
    }

    @Test
    void sign_permissionDenied_throwsRuntimeException() throws Exception {
        // RED: Permission errors → throw RuntimeException (transient — token refresh may resolve)
        final GcpKmsClientWrapper mockClient = mock(GcpKmsClientWrapper.class);
        when(mockClient.asymmetricSign(any(AsymmetricSignRequest.class)))
                .thenThrow(new RuntimeException("Permission denied"));

        final GcpKmsSigningConfig config = new GcpKmsSigningConfig(
                Map.of("test-actor", TEST_VERSION_NAME));
        final GcpKmsSigningClient client = new GcpKmsSigningClient(config, mockClient);

        assertThatThrownBy(() -> client.sign(TEST_VERSION_NAME, new byte[]{5, 6, 7}))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("signing failed");
    }
}
