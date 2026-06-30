package io.casehub.ledger.signing.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

/**
 * Pure Java unit tests for {@link AwsKmsSigningClient}.
 * Uses Mockito to mock {@link KmsClient} — no WireMock needed since KmsClient is a Java interface.
 */
class AwsKmsSigningClientTest {

    private static final String TEST_KEY_ARN = "arn:aws:kms:us-east-1:123456789012:key/test-key-id";

    // Real P-256 public key DER bytes (from test fixture)
    private static final byte[] P256_PUBLIC_KEY_DER = Base64.getDecoder().decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+xVOdphkfpEtl7OF8oCyvWw31dR13cJjaJy"
                    + "LEqiZbUQAYs+TUpJDVB+Z5c3qGfLTXJCQ6TLG0dY6qfQCcVn0Aw==");

    @Test
    void sign_p256Key_returnsSignatureBytes() {
        // RED: Write failing test first — no implementation exists yet
        final KmsClient mockKms = mock(KmsClient.class);
        final byte[] signatureBytes = new byte[]{1, 2, 3, 4};

        // Mock sign
        when(mockKms.sign(any(SignRequest.class))).thenReturn(
                SignResponse.builder()
                        .signature(SdkBytes.fromByteArray(signatureBytes))
                        .build());

        final AwsKmsSigningConfig config = new AwsKmsSigningConfig(
                "us-east-1",
                Map.of("test-actor", TEST_KEY_ARN));
        final AwsKmsSigningClient client = new AwsKmsSigningClient(config, mockKms);

        final byte[] result = client.sign(TEST_KEY_ARN, new byte[]{5, 6, 7}, SigningAlgorithmSpec.ECDSA_SHA_256);

        assertThat(result).isEqualTo(signatureBytes);
    }

    @Test
    void fetchPublicKey_p256Key_returnsECPublicKey() {
        // RED: Write failing test first
        final KmsClient mockKms = mock(KmsClient.class);
        when(mockKms.getPublicKey(any(GetPublicKeyRequest.class))).thenReturn(
                GetPublicKeyResponse.builder()
                        .keySpec(KeySpec.ECC_NIST_P256)
                        .publicKey(SdkBytes.fromByteArray(P256_PUBLIC_KEY_DER))
                        .build());

        final AwsKmsSigningConfig config = new AwsKmsSigningConfig(
                "us-east-1",
                Map.of("test-actor", TEST_KEY_ARN));
        final AwsKmsSigningClient client = new AwsKmsSigningClient(config, mockKms);

        final AwsKmsKeyInfo result = client.fetchPublicKey(TEST_KEY_ARN);

        assertThat(result).isNotNull();
        assertThat(result.publicKey()).isNotNull();
        assertThat(result.publicKey().getAlgorithm()).isEqualTo("EC");
        assertThat(result.signingAlgorithm()).isEqualTo(SigningAlgorithmSpec.ECDSA_SHA_256);
    }

    @Test
    void fetchPublicKey_rsaKey_returnsNull() {
        // RED: RSA keys are rejected — fetchPublicKey returns null
        final KmsClient mockKms = mock(KmsClient.class);
        when(mockKms.getPublicKey(any(GetPublicKeyRequest.class))).thenReturn(
                GetPublicKeyResponse.builder()
                        .keySpec(KeySpec.RSA_2048)
                        .publicKey(SdkBytes.fromByteArray(new byte[]{1, 2, 3}))
                        .build());

        final AwsKmsSigningConfig config = new AwsKmsSigningConfig(
                "us-east-1",
                Map.of("test-actor", TEST_KEY_ARN));
        final AwsKmsSigningClient client = new AwsKmsSigningClient(config, mockKms);

        final AwsKmsKeyInfo result = client.fetchPublicKey(TEST_KEY_ARN);

        assertThat(result).isNull();
    }

    @Test
    void fetchPublicKey_notFound_returnsNull() {
        // RED: Key not found → return null (cached as absent)
        final KmsClient mockKms = mock(KmsClient.class);
        when(mockKms.getPublicKey(any(GetPublicKeyRequest.class)))
                .thenThrow(NotFoundException.builder().message("Key not found").build());

        final AwsKmsSigningConfig config = new AwsKmsSigningConfig(
                "us-east-1",
                Map.of("test-actor", TEST_KEY_ARN));
        final AwsKmsSigningClient client = new AwsKmsSigningClient(config, mockKms);

        final AwsKmsKeyInfo result = client.fetchPublicKey(TEST_KEY_ARN);

        assertThat(result).isNull();
    }

    @Test
    void sign_transientError_throwsRuntimeException() {
        // RED: Transient KMS errors → throw RuntimeException
        final KmsClient mockKms = mock(KmsClient.class);
        when(mockKms.sign(any(SignRequest.class)))
                .thenThrow(software.amazon.awssdk.services.kms.model.KmsException.builder()
                        .statusCode(429)
                        .message("Rate limit exceeded")
                        .build());

        final AwsKmsSigningConfig config = new AwsKmsSigningConfig(
                "us-east-1",
                Map.of("test-actor", TEST_KEY_ARN));
        final AwsKmsSigningClient client = new AwsKmsSigningClient(config, mockKms);

        assertThatThrownBy(() -> client.sign(TEST_KEY_ARN, new byte[]{5, 6, 7}, SigningAlgorithmSpec.ECDSA_SHA_256))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("signing failed");
    }
}
