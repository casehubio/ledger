package io.casehub.ledger.signing.azure;

import com.azure.security.keyvault.keys.cryptography.models.SignResult;
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm;
import com.azure.security.keyvault.keys.models.KeyVaultKey;

/**
 * Thin interface wrapper around Azure Key Vault {@code KeyClient} and {@code CryptographyClient}.
 *
 * <p>Both SDK clients are concrete classes (not interfaces), so they cannot be mocked
 * directly with Mockito. This wrapper allows testing without WireMock complexity.
 *
 * <p>Production code uses {@link DefaultAzureKeyVaultClientWrapper} (delegates to real SDK).
 * Tests inject a Mockito mock of this interface.
 */
public interface AzureKeyVaultClientWrapper {

    /**
     * Gets a key from Azure Key Vault.
     *
     * @param vaultUrl Azure Key Vault URL (e.g., "https://vault-name.vault.azure.net")
     * @param keyName  key name
     * @return the key
     */
    KeyVaultKey getKey(String vaultUrl, String keyName);

    /**
     * Signs a digest via Azure Key Vault.
     *
     * @param vaultUrl  Azure Key Vault URL
     * @param keyName   key name
     * @param algorithm signature algorithm (ES256, ES384, ES512)
     * @param digest    pre-computed digest bytes
     * @return the sign result
     */
    SignResult sign(String vaultUrl, String keyName, SignatureAlgorithm algorithm, byte[] digest);
}
