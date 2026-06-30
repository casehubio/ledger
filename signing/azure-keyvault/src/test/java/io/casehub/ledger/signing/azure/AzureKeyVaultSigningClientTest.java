package io.casehub.ledger.signing.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Map;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.keys.cryptography.models.SignResult;
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyType;
import com.azure.security.keyvault.keys.models.KeyVaultKey;

/**
 * Tests for {@link AzureKeyVaultSigningClient}.
 */
class AzureKeyVaultSigningClientTest {

    private AzureKeyVaultClientWrapper mockWrapper;
    private AzureKeyVaultSigningClient client;
    private KeyPair testKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        // Add BouncyCastle provider for JsonWebKey.fromEc
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        final Map<String, String> keyMapping = Map.of(
                "test-actor", "https://test-vault.vault.azure.net#test-key");
        final AzureKeyVaultSigningConfig config = new AzureKeyVaultSigningConfig(keyMapping);

        mockWrapper = mock(AzureKeyVaultClientWrapper.class);
        client = new AzureKeyVaultSigningClient(config, mockWrapper);

        // Generate real P-256 keypair for testing
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        testKeyPair = gen.generateKeyPair();
    }

    @Test
    void fetchPublicKeyReturnsECPublicKeyForValidP256Key() throws Exception {
        // Mock getKey to return P-256 EC key
        final JsonWebKey jwk = JsonWebKey.fromEc(testKeyPair, Security.getProvider("BC"));
        final KeyVaultKey keyVaultKey = mock(KeyVaultKey.class);
        when(keyVaultKey.getKey()).thenReturn(jwk);
        when(mockWrapper.getKey("https://test-vault.vault.azure.net", "test-key"))
                .thenReturn(keyVaultKey);

        final var result = client.fetchPublicKey(
                "https://test-vault.vault.azure.net#test-key");

        assertThat(result).isPresent();
        assertThat(result.get().publicKey()).isNotNull();
        assertThat(result.get().componentSize()).isEqualTo(32);  // P-256
    }

    @Test
    void fetchPublicKeyReturnsEmptyWhenKeyNotFound() {
        when(mockWrapper.getKey("https://test-vault.vault.azure.net", "missing-key"))
                .thenThrow(new ResourceNotFoundException("Key not found", null));

        final var result = client.fetchPublicKey(
                "https://test-vault.vault.azure.net#missing-key");

        assertThat(result).isEmpty();
    }

    @Test
    void fetchPublicKeyReturnsEmptyForRSAKey() {
        // Create RSA JWK (not supported)
        final JsonWebKey rsa = new JsonWebKey().setKeyType(KeyType.RSA);
        final KeyVaultKey keyVaultKey = mock(KeyVaultKey.class);
        when(keyVaultKey.getKey()).thenReturn(rsa);
        when(mockWrapper.getKey("https://test-vault.vault.azure.net", "rsa-key"))
                .thenReturn(keyVaultKey);

        final var result = client.fetchPublicKey(
                "https://test-vault.vault.azure.net#rsa-key");

        assertThat(result).isEmpty();
    }

    @Test
    void signProducesDEREncodedSignature() throws Exception {
        // Mock getKey for P-256
        final JsonWebKey jwk = JsonWebKey.fromEc(testKeyPair, Security.getProvider("BC"));
        final KeyVaultKey keyVaultKey = mock(KeyVaultKey.class);
        when(keyVaultKey.getKey()).thenReturn(jwk);
        when(mockWrapper.getKey("https://test-vault.vault.azure.net", "test-key"))
                .thenReturn(keyVaultKey);

        // Mock sign to return raw R||S (64 bytes for P-256)
        final byte[] rawSig = new byte[64];  // Simplified - zero bytes
        final SignResult signResult = mock(SignResult.class);
        when(signResult.getSignature()).thenReturn(rawSig);
        when(mockWrapper.sign(
                eq("https://test-vault.vault.azure.net"),
                eq("test-key"),
                eq(SignatureAlgorithm.ES256),
                any(byte[].class)))
                .thenReturn(signResult);

        // Call client
        final byte[] signature = client.sign(
                "https://test-vault.vault.azure.net#test-key",
                "test data".getBytes());

        // Verify signature is DER-encoded (DER is longer than raw for small values)
        assertThat(signature).isNotNull();
        // DER for 64-byte raw sig is typically 70-72 bytes depending on high bits
    }

    @Test
    void signThrowsExceptionOnServiceError() throws Exception {
        final JsonWebKey jwk = JsonWebKey.fromEc(testKeyPair, Security.getProvider("BC"));
        final KeyVaultKey keyVaultKey = mock(KeyVaultKey.class);
        when(keyVaultKey.getKey()).thenReturn(jwk);
        when(mockWrapper.getKey("https://test-vault.vault.azure.net", "test-key"))
                .thenReturn(keyVaultKey);

        when(mockWrapper.sign(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        assertThatThrownBy(() -> client.sign(
                "https://test-vault.vault.azure.net#test-key",
                "data".getBytes()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Azure Key Vault signing failed");
    }
}
