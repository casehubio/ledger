package io.casehub.ledger.signing.azure;

import java.util.Map;

/**
 * Configuration for Azure Key Vault signing client.
 *
 * <p>No framework dependencies — plain Java record.
 *
 * @param keyMapping actorId → vault URL and key name (format: "vaultUrl#keyName")
 */
public record AzureKeyVaultSigningConfig(Map<String, String> keyMapping) {
}
