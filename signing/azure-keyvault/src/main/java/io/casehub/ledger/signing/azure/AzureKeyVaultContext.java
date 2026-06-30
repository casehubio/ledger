package io.casehub.ledger.signing.azure;

import java.security.PublicKey;

/**
 * Cached context for a single Azure Key Vault key.
 *
 * <p>Held by {@code AzureKeyVaultAgentSigner}'s context cache, keyed by actorId.
 *
 * @param keyName key name in Azure Key Vault
 * @param vaultUrl Azure Key Vault URL (e.g., "https://vault-name.vault.azure.net")
 * @param publicKey parsed EC public key
 * @param componentSize R and S component size in bytes (P-256: 32, P-384: 48, P-521: 66)
 */
public record AzureKeyVaultContext(
        String keyName,
        String vaultUrl,
        PublicKey publicKey,
        int componentSize) {
}
