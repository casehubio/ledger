package io.casehub.ledger.signing.azure.quarkus;

import java.util.Map;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Quarkus config mapping for Azure Key Vault signing.
 *
 * <p>Bridges Quarkus config properties to the pure Java {@code AzureKeyVaultSigningConfig} POJO.
 *
 * <p><strong>Activation:</strong>
 * <pre>
 * quarkus.arc.selected-alternatives=io.casehub.ledger.signing.azure.quarkus.AzureKeyVaultAgentSigner
 * </pre>
 */
@ConfigMapping(prefix = "casehub.ledger.azure-keyvault")
public interface AzureKeyVaultConfig {

    /**
     * Actor ID → key reference mapping (format: "vaultUrl#keyName").
     */
    Map<String, String> keyMapping();

    /**
     * Context cache refresh interval.
     * Default: 24 hours.
     */
    @WithDefault("24h")
    String refreshInterval();
}
