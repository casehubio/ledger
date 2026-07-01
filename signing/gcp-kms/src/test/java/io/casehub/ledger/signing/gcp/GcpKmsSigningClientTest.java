package io.casehub.ledger.signing.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.google.cloud.kms.v1.AsymmetricSignRequest;
import com.google.cloud.kms.v1.AsymmetricSignResponse;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm;
import com.google.protobuf.ByteString;

class GcpKmsSigningClientTest {

    private static final String TEST_VERSION_NAME =
            "projects/test-project/locations/us-central1/keyRings/test-ring/cryptoKeys/test-key/cryptoKeyVersions/1";

    private static final String P256_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+xVOdphkfpEtl7OF8oCyvWw31dR1
            3cJjaJyLEqiZbUQAYs+TUpJDVB+Z5c3qGfLTXJCQ6TLG0dY6qfQCcVn0Aw==
            -----END PUBLIC KEY-----
            """;

    private static final String P384_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEYClWa7dIOzTQptfnAtSoL0KZbKugqruX
            CqPajpYKx3wegjqG40EqXDKj3GsdE8eyqGwgad/KKHP0vGtClQjM1IbMypWQD/th
            +AR0tMFPRgpAxBHLAbyG8d5HcxdGOUjT
            -----END PUBLIC KEY-----
            """;

    @Test
    void sign_p256Key_usesCorrectDigest() {
        final GcpKmsClientWrapper mockWrapper = mock(GcpKmsClientWrapper.class);
        final byte[] signatureBytes = new byte[]{1, 2, 3, 4};

        when(mockWrapper.asymmetricSign(any(AsymmetricSignRequest.class))).thenReturn(
                AsymmetricSignResponse.newBuilder()
                        .setSignature(ByteString.copyFrom(signatureBytes))
                        .build());

        final GcpKmsSigningClient client = new GcpKmsSigningClient(mockWrapper);

        final byte[] result = client.sign(TEST_VERSION_NAME,
                CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256, new byte[]{5, 6, 7});

        assertThat(result).isEqualTo(signatureBytes);
    }

    @Test
    void sign_p384Key_usesCorrectDigest() {
        final GcpKmsClientWrapper mockWrapper = mock(GcpKmsClientWrapper.class);
        final byte[] signatureBytes = new byte[]{1, 2, 3, 4};

        when(mockWrapper.asymmetricSign(any(AsymmetricSignRequest.class))).thenReturn(
                AsymmetricSignResponse.newBuilder()
                        .setSignature(ByteString.copyFrom(signatureBytes))
                        .build());

        final GcpKmsSigningClient client = new GcpKmsSigningClient(mockWrapper);

        final byte[] result = client.sign(TEST_VERSION_NAME,
                CryptoKeyVersionAlgorithm.EC_SIGN_P384_SHA384, new byte[]{5, 6, 7});

        assertThat(result).isEqualTo(signatureBytes);
    }

    @Test
    void fetchPublicKey_p256Key_returnsContext() {
        final GcpKmsClientWrapper mockWrapper = mock(GcpKmsClientWrapper.class);
        when(mockWrapper.getPublicKey(TEST_VERSION_NAME)).thenReturn(
                com.google.cloud.kms.v1.PublicKey.newBuilder()
                        .setPem(P256_PUBLIC_KEY_PEM)
                        .setAlgorithm(CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256)
                        .build());

        final GcpKmsSigningClient client = new GcpKmsSigningClient(mockWrapper);

        final GcpKmsContext result = client.fetchPublicKey(TEST_VERSION_NAME);

        assertThat(result).isNotNull();
        assertThat(result.publicKey().getAlgorithm()).isEqualTo("EC");
        assertThat(result.algorithm()).isEqualTo(CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256);
        assertThat(result.versionName()).isEqualTo(TEST_VERSION_NAME);
    }

    @Test
    void fetchPublicKey_p384Key_returnsContext() {
        final GcpKmsClientWrapper mockWrapper = mock(GcpKmsClientWrapper.class);
        when(mockWrapper.getPublicKey(TEST_VERSION_NAME)).thenReturn(
                com.google.cloud.kms.v1.PublicKey.newBuilder()
                        .setPem(P384_PUBLIC_KEY_PEM)
                        .setAlgorithm(CryptoKeyVersionAlgorithm.EC_SIGN_P384_SHA384)
                        .build());

        final GcpKmsSigningClient client = new GcpKmsSigningClient(mockWrapper);

        final GcpKmsContext result = client.fetchPublicKey(TEST_VERSION_NAME);

        assertThat(result).isNotNull();
        assertThat(result.publicKey().getAlgorithm()).isEqualTo("EC");
        assertThat(result.algorithm()).isEqualTo(CryptoKeyVersionAlgorithm.EC_SIGN_P384_SHA384);
    }

    @Test
    void fetchPublicKey_rsaKey_returnsNull() {
        final GcpKmsClientWrapper mockWrapper = mock(GcpKmsClientWrapper.class);
        when(mockWrapper.getPublicKey(TEST_VERSION_NAME)).thenReturn(
                com.google.cloud.kms.v1.PublicKey.newBuilder()
                        .setPem("-----BEGIN PUBLIC KEY-----\nRSA\n-----END PUBLIC KEY-----")
                        .setAlgorithm(CryptoKeyVersionAlgorithm.RSA_SIGN_PKCS1_2048_SHA256)
                        .build());

        final GcpKmsSigningClient client = new GcpKmsSigningClient(mockWrapper);

        final GcpKmsContext result = client.fetchPublicKey(TEST_VERSION_NAME);

        assertThat(result).isNull();
    }

    @Test
    void fetchPublicKey_notFound_returnsNull() {
        final GcpKmsClientWrapper mockWrapper = mock(GcpKmsClientWrapper.class);
        when(mockWrapper.getPublicKey(TEST_VERSION_NAME))
                .thenThrow(new RuntimeException("NOT_FOUND: Key version not found"));

        final GcpKmsSigningClient client = new GcpKmsSigningClient(mockWrapper);

        final GcpKmsContext result = client.fetchPublicKey(TEST_VERSION_NAME);

        assertThat(result).isNull();
    }

    @Test
    void sign_permissionDenied_throwsRuntimeException() {
        final GcpKmsClientWrapper mockWrapper = mock(GcpKmsClientWrapper.class);
        when(mockWrapper.asymmetricSign(any(AsymmetricSignRequest.class)))
                .thenThrow(new RuntimeException("Permission denied"));

        final GcpKmsSigningClient client = new GcpKmsSigningClient(mockWrapper);

        assertThatThrownBy(() -> client.sign(TEST_VERSION_NAME,
                CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256, new byte[]{5, 6, 7}))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("signing failed");
    }
}
