package io.casehub.ledger.signing.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AzureKeyVaultContext}.
 */
class AzureKeyVaultContextTest {

    @Test
    void createsContextWithAllFields() throws Exception {
        final KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        final KeyPair keyPair = gen.generateKeyPair();
        final PublicKey publicKey = keyPair.getPublic();

        final AzureKeyVaultContext context = new AzureKeyVaultContext(
                "test-key",
                "https://test-vault.vault.azure.net",
                publicKey,
                32);

        assertThat(context.keyName()).isEqualTo("test-key");
        assertThat(context.vaultUrl()).isEqualTo("https://test-vault.vault.azure.net");
        assertThat(context.publicKey()).isEqualTo(publicKey);
        assertThat(context.componentSize()).isEqualTo(32);
    }

    @Test
    void componentSizeMatchesCurve() throws Exception {
        // P-256 → 32 bytes
        final KeyPairGenerator gen256 = KeyPairGenerator.getInstance("EC");
        gen256.initialize(new ECGenParameterSpec("secp256r1"));
        final PublicKey p256 = gen256.generateKeyPair().getPublic();

        final AzureKeyVaultContext ctx256 = new AzureKeyVaultContext(
                "key-p256", "https://vault.azure.net", p256, 32);
        assertThat(ctx256.componentSize()).isEqualTo(32);

        // P-384 → 48 bytes
        final KeyPairGenerator gen384 = KeyPairGenerator.getInstance("EC");
        gen384.initialize(new ECGenParameterSpec("secp384r1"));
        final PublicKey p384 = gen384.generateKeyPair().getPublic();

        final AzureKeyVaultContext ctx384 = new AzureKeyVaultContext(
                "key-p384", "https://vault.azure.net", p384, 48);
        assertThat(ctx384.componentSize()).isEqualTo(48);
    }
}
