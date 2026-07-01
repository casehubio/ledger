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

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.keys.cryptography.models.SignResult;
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyType;
import com.azure.security.keyvault.keys.models.KeyVaultKey;

class AzureKeyVaultSigningClientTest {

    private AzureKeyVaultClientWrapper mockWrapper;
    private AzureKeyVaultSigningClient client;
    private KeyPair testKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        mockWrapper = mock(AzureKeyVaultClientWrapper.class);
        client = new AzureKeyVaultSigningClient(mockWrapper);

        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        testKeyPair = gen.generateKeyPair();
    }

    @Test
    void fetchPublicKeyReturnsContextWithAlgorithmForP256Key() {
        final JsonWebKey jwk = JsonWebKey.fromEc(testKeyPair, Security.getProvider("BC"));
        final KeyVaultKey keyVaultKey = mock(KeyVaultKey.class);
        when(keyVaultKey.getKey()).thenReturn(jwk);
        when(mockWrapper.getKey("https://test-vault.vault.azure.net", "test-key"))
                .thenReturn(keyVaultKey);

        final var result = client.fetchPublicKey(
                "https://test-vault.vault.azure.net#test-key");

        assertThat(result).isPresent();
        assertThat(result.get().publicKey()).isNotNull();
        assertThat(result.get().algorithm()).isEqualTo(SignatureAlgorithm.ES256);
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
    void signProducesDEREncodedSignature() {
        final byte[] rawSig = new byte[64];
        final SignResult signResult = mock(SignResult.class);
        when(signResult.getSignature()).thenReturn(rawSig);
        when(mockWrapper.sign(
                eq("https://test-vault.vault.azure.net"),
                eq("test-key"),
                eq(SignatureAlgorithm.ES256),
                any(byte[].class)))
                .thenReturn(signResult);

        final byte[] signature = client.sign(
                "https://test-vault.vault.azure.net", "test-key",
                SignatureAlgorithm.ES256, "test data".getBytes());

        assertThat(signature).isNotNull();
    }

    @Test
    void signThrowsExceptionOnServiceError() {
        when(mockWrapper.sign(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        assertThatThrownBy(() -> client.sign(
                "https://test-vault.vault.azure.net", "test-key",
                SignatureAlgorithm.ES256, "data".getBytes()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Azure Key Vault signing failed");
    }
}
