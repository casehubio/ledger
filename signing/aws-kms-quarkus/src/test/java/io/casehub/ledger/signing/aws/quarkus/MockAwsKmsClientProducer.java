package io.casehub.ledger.signing.aws.quarkus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;

/**
 * Test CDI producer for {@link AwsKmsAgentSigner} with a mocked {@link KmsClient}.
 *
 * <p>Generates a real P-256 key pair at startup and uses it to sign data.
 * The mock returns the real public key and real signatures so verification round-trips work.
 */
@ApplicationScoped
public class MockAwsKmsClientProducer {

    private final KeyPair keyPair;

    public MockAwsKmsClientProducer() throws Exception {
        // Generate a real P-256 key pair for signing
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        this.keyPair = gen.generateKeyPair();
    }

    @Produces
    @Alternative
    @Priority(2)
    @ApplicationScoped
    public AwsKmsAgentSigner produceAwsKmsAgentSigner(final AwsKmsConfig config) {
        final KmsClient mockKms = mock(KmsClient.class);

        // Mock getPublicKey — return P-256 key spec and DER bytes from the generated keypair
        when(mockKms.getPublicKey(any(GetPublicKeyRequest.class))).thenReturn(
                GetPublicKeyResponse.builder()
                        .keySpec(KeySpec.ECC_NIST_P256)
                        .publicKey(SdkBytes.fromByteArray(keyPair.getPublic().getEncoded()))
                        .build());

        // Mock sign — perform real ECDSA signing with the generated key pair
        when(mockKms.sign(any(SignRequest.class))).thenAnswer(invocation -> {
            final SignRequest req = invocation.getArgument(0);
            final byte[] data = req.message().asByteArray();
            final byte[] signature = signWithRealKey(data);
            return SignResponse.builder()
                    .signature(SdkBytes.fromByteArray(signature))
                    .build();
        });

        // Use the package-private test constructor that accepts a KmsClient
        return new AwsKmsAgentSigner(config, mockKms);
    }

    private byte[] signWithRealKey(final byte[] data) {
        try {
            final Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initSign(keyPair.getPrivate());
            sig.update(data);
            return sig.sign();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to sign with test key", e);
        }
    }
}
