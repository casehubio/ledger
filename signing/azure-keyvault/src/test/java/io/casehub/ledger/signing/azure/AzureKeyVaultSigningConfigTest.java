package io.casehub.ledger.signing.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AzureKeyVaultSigningConfig}.
 */
class AzureKeyVaultSigningConfigTest {

    @Test
    void createsConfigWithKeyMapping() {
        final Map<String, String> keyMapping = Map.of(
                "actor1", "https://vault1.vault.azure.net#key1",
                "actor2", "https://vault2.vault.azure.net#key2");

        final AzureKeyVaultSigningConfig config = new AzureKeyVaultSigningConfig(keyMapping);

        assertThat(config.keyMapping()).isEqualTo(keyMapping);
    }

    @Test
    void parsesVaultUrlAndKeyNameFromMapping() {
        final Map<String, String> keyMapping = Map.of(
                "test-actor", "https://test-vault.vault.azure.net#test-key");
        final AzureKeyVaultSigningConfig config = new AzureKeyVaultSigningConfig(keyMapping);

        final String fullRef = config.keyMapping().get("test-actor");

        assertThat(fullRef).isEqualTo("https://test-vault.vault.azure.net#test-key");
        assertThat(fullRef.split("#")).hasSize(2);
        assertThat(fullRef.split("#")[0]).isEqualTo("https://test-vault.vault.azure.net");
        assertThat(fullRef.split("#")[1]).isEqualTo("test-key");
    }
}
