package io.casehub.ledger.signing.azure;

import java.security.PublicKey;

import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm;

/**
 * Cached context for a single Azure Key Vault key.
 *
 * @param keyName key name in Azure Key Vault
 * @param vaultUrl Azure Key Vault URL (e.g., "https://vault-name.vault.azure.net")
 * @param publicKey parsed EC public key
 * @param algorithm signature algorithm (ES256, ES384, ES512)
 */
public record AzureKeyVaultContext(
        String keyName,
        String vaultUrl,
        PublicKey publicKey,
        SignatureAlgorithm algorithm) {
}
