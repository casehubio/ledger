package io.casehub.ledger.signing.vault;

import java.util.Map;

/**
 * Configuration for the Vault Transit signing client.
 *
 * <p>Plain Java record — no framework annotations. The Quarkus adapter bridges
 * {@code @ConfigMapping} to this POJO.
 *
 * @param address    base URL of the Vault instance, e.g. {@code http://localhost:8200}
 * @param keyMapping actorId to Vault Transit key name mapping
 */
public record VaultTransitSigningConfig(
        String address,
        Map<String, String> keyMapping) {}
