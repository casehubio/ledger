package io.casehub.ledger.signing.gcp.quarkus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import com.google.cloud.kms.v1.AsymmetricSignRequest;
import com.google.cloud.kms.v1.AsymmetricSignResponse;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.protobuf.ByteString;

import io.casehub.ledger.signing.gcp.GcpKmsClientWrapper;
import io.casehub.ledger.signing.gcp.GcpKmsSigningClient;

/**
 * Test CDI producer for {@link GcpKmsAgentSigner} with a mocked {@link GcpKmsClientWrapper}.
 *
 * <p>Generates a real P-256 key pair at startup and uses it to sign data.
 * The mock returns the real public key and real signatures so verification round-trips work.
 */
@ApplicationScoped
public class MockGcpKmsClientProducer {

    private final KeyPair keyPair;

    public MockGcpKmsClientProducer() throws Exception {
        // Generate a real P-256 key pair for signing
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        this.keyPair = gen.generateKeyPair();
    }

    @Produces
    @Alternative
    @Priority(2)
    @ApplicationScoped
    public GcpKmsAgentSigner produceGcpKmsAgentSigner(final GcpKmsConfig config) {
        final GcpKmsClientWrapper mockWrapper = mock(GcpKmsClientWrapper.class);

        // Mock getPublicKey — return P-256 PEM from the generated keypair
        when(mockWrapper.getPublicKey(any(String.class))).thenReturn(
                com.google.cloud.kms.v1.PublicKey.newBuilder()
                        .setPem(toPem(keyPair.getPublic().getEncoded()))
                        .setAlgorithm(CryptoKeyVersion.CryptoKeyVersionAlgorithm.EC_SIGN_P256_SHA256)
                        .build());

        // Mock asymmetricSign — perform real ECDSA signing with the generated key pair
        when(mockWrapper.asymmetricSign(any(AsymmetricSignRequest.class))).thenAnswer(invocation -> {
            final AsymmetricSignRequest req = invocation.getArgument(0);
            // Extract digest bytes from the request
            final byte[] digest = req.getDigest().getSha256().toByteArray();
            // Sign the already-hashed data (GCP KMS receives a digest, not raw data)
            final byte[] signature = signPrehashedWithRealKey(digest);
            return AsymmetricSignResponse.newBuilder()
                    .setSignature(ByteString.copyFrom(signature))
                    .build();
        });

        final GcpKmsSigningClient client = new GcpKmsSigningClient(mockWrapper);

        // Use the package-private test constructor that accepts a client
        return new GcpKmsAgentSigner(config, client);
    }

    private byte[] signPrehashedWithRealKey(final byte[] digest) {
        try {
            // Use NONEwithECDSA because the data is already hashed
            final Signature sig = Signature.getInstance("NONEwithECDSA");
            sig.initSign(keyPair.getPrivate());
            sig.update(digest);
            return sig.sign();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to sign with test key", e);
        }
    }

    private String toPem(final byte[] derBytes) {
        final String base64 = java.util.Base64.getEncoder().encodeToString(derBytes);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }
}
