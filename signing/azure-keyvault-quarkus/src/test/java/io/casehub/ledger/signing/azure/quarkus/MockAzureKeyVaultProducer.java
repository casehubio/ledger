package io.casehub.ledger.signing.azure.quarkus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import com.azure.security.keyvault.keys.cryptography.models.SignResult;
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyType;
import com.azure.security.keyvault.keys.models.KeyVaultKey;

import io.casehub.ledger.signing.azure.AzureKeyVaultClientWrapper;

/**
 * Test CDI producer for {@link AzureKeyVaultAgentSigner} with a mocked {@link AzureKeyVaultClientWrapper}.
 *
 * <p>Generates a real P-256 key pair at startup and uses it to sign data.
 * The mock returns the real public key and real signatures (converted to raw R‖S format)
 * so verification round-trips work.
 */
@ApplicationScoped
public class MockAzureKeyVaultProducer {

    private final KeyPair keyPair;
    private final ECPublicKey ecPublicKey;

    public MockAzureKeyVaultProducer() throws Exception {
        // Generate a real P-256 key pair for signing
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        this.keyPair = gen.generateKeyPair();
        this.ecPublicKey = (ECPublicKey) keyPair.getPublic();
    }

    @Produces
    @Alternative
    @Priority(2)
    @ApplicationScoped
    public AzureKeyVaultAgentSigner produceAzureKeyVaultAgentSigner(final AzureKeyVaultConfig config) {
        final AzureKeyVaultClientWrapper mockWrapper = mock(AzureKeyVaultClientWrapper.class);

        // Mock getKey — return EC key type and real public key
        when(mockWrapper.getKey(anyString(), anyString())).thenAnswer(invocation -> {
            final JsonWebKey jwk = mock(JsonWebKey.class);
            when(jwk.getKeyType()).thenReturn(KeyType.EC);
            when(jwk.toEc()).thenReturn(keyPair);

            final KeyVaultKey keyVaultKey = mock(KeyVaultKey.class);
            when(keyVaultKey.getKey()).thenReturn(jwk);
            return keyVaultKey;
        });

        // Mock sign — perform real ECDSA signing with the generated key pair,
        // then convert DER output to raw R||S format (reverse of EcSignatureConverter)
        when(mockWrapper.sign(anyString(), anyString(), any(SignatureAlgorithm.class), any(byte[].class)))
                .thenAnswer(invocation -> {
                    final byte[] digest = invocation.getArgument(3);
                    final byte[] derSignature = signDigestWithRealKey(digest);
                    final byte[] rawSignature = derToRaw(derSignature, 32);  // P-256 component size

                    // Mock SignResult to return the raw signature
                    final SignResult mockResult = mock(SignResult.class);
                    when(mockResult.getSignature()).thenReturn(rawSignature);
                    return mockResult;
                });

        // Use the package-private test constructor that accepts a wrapper
        return new AzureKeyVaultAgentSigner(config, mockWrapper);
    }

    private byte[] signDigestWithRealKey(final byte[] digest) {
        try {
            // Sign the digest using NONEwithECDSA (no additional hashing)
            final Signature sig = Signature.getInstance("NONEwithECDSA");
            sig.initSign(keyPair.getPrivate());
            sig.update(digest);
            return sig.sign();  // Returns DER-encoded ECDSA signature
        } catch (final Exception e) {
            throw new RuntimeException("Failed to sign with test key", e);
        }
    }

    /**
     * Converts DER-encoded ECDSA signature to raw R‖S format.
     *
     * <p>Reverse of {@code EcSignatureConverter.rawToDer()}.
     *
     * @param derSignature DER-encoded SEQUENCE { INTEGER r, INTEGER s }
     * @param componentSize R and S component size in bytes (e.g., 32 for P-256)
     * @return concatenated R‖S bytes (length = 2 × componentSize)
     */
    private static byte[] derToRaw(final byte[] derSignature, final int componentSize) {
        try {
            // DER format: 0x30 [total-len] 0x02 [r-len] [r-bytes] 0x02 [s-len] [s-bytes]
            int offset = 0;

            // Skip SEQUENCE tag and length
            if (derSignature[offset++] != 0x30) {
                throw new IllegalArgumentException("Expected SEQUENCE tag");
            }
            offset++;  // Skip total length

            // Parse R INTEGER
            if (derSignature[offset++] != 0x02) {
                throw new IllegalArgumentException("Expected INTEGER tag for R");
            }
            final int rLen = derSignature[offset++] & 0xFF;
            final BigInteger r = new BigInteger(1, derSignature, offset, rLen);
            offset += rLen;

            // Parse S INTEGER
            if (derSignature[offset++] != 0x02) {
                throw new IllegalArgumentException("Expected INTEGER tag for S");
            }
            final int sLen = derSignature[offset++] & 0xFF;
            final BigInteger s = new BigInteger(1, derSignature, offset, sLen);

            // Convert to raw bytes with fixed component size (pad with leading zeros if needed)
            final byte[] rBytes = toFixedLengthBytes(r, componentSize);
            final byte[] sBytes = toFixedLengthBytes(s, componentSize);

            // Concatenate R||S
            final byte[] rawSignature = new byte[componentSize * 2];
            System.arraycopy(rBytes, 0, rawSignature, 0, componentSize);
            System.arraycopy(sBytes, 0, rawSignature, componentSize, componentSize);

            return rawSignature;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to convert DER signature to raw R||S", e);
        }
    }

    /**
     * Converts BigInteger to fixed-length byte array.
     * Pads with leading zeros if the value is shorter than targetLength.
     * Strips the leading zero byte if BigInteger.toByteArray() added one for sign.
     */
    private static byte[] toFixedLengthBytes(final BigInteger value, final int targetLength) {
        final byte[] bytes = value.toByteArray();
        if (bytes.length == targetLength) {
            return bytes;
        } else if (bytes.length > targetLength) {
            // BigInteger added a leading 0x00 for sign — strip it
            if (bytes[0] == 0 && bytes.length == targetLength + 1) {
                final byte[] stripped = new byte[targetLength];
                System.arraycopy(bytes, 1, stripped, 0, targetLength);
                return stripped;
            } else {
                throw new IllegalArgumentException("Component too large for target length");
            }
        } else {
            // Value is shorter — pad with leading zeros
            final byte[] padded = new byte[targetLength];
            System.arraycopy(bytes, 0, padded, targetLength - bytes.length, bytes.length);
            return padded;
        }
    }
}
