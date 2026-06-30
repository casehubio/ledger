package io.casehub.ledger.signing.azure;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.KeyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.SignResult;
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm;
import com.azure.security.keyvault.keys.models.KeyVaultKey;

/**
 * Production implementation of {@link AzureKeyVaultClientWrapper}.
 *
 * <p>Delegates to real Azure SDK clients ({@code KeyClient} and {@code CryptographyClient}).
 *
 * <p>Uses {@code DefaultAzureCredential} for authentication (env vars, managed identity, Azure CLI).
 */
public class DefaultAzureKeyVaultClientWrapper implements AzureKeyVaultClientWrapper {

    @Override
    public KeyVaultKey getKey(final String vaultUrl, final String keyName) {
        final KeyClient keyClient = new KeyClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
        return keyClient.getKey(keyName);
    }

    @Override
    public SignResult sign(final String vaultUrl, final String keyName,
            final SignatureAlgorithm algorithm, final byte[] digest) {
        final CryptographyClient cryptoClient = new CryptographyClientBuilder()
                .credential(new DefaultAzureCredentialBuilder().build())
                .keyIdentifier(vaultUrl + "/keys/" + keyName)
                .buildClient();
        return cryptoClient.sign(algorithm, digest);
    }
}
