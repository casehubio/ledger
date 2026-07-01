package io.casehub.ledger.signing.azure;

import java.util.concurrent.ConcurrentHashMap;

import com.azure.identity.DefaultAzureCredential;
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
 * <p>Caches {@link KeyClient} per vault URL and {@link CryptographyClient} per key identifier.
 * {@link DefaultAzureCredential} is created once and shared — it's designed for reuse and
 * scans multiple credential providers on each construction.
 */
public class DefaultAzureKeyVaultClientWrapper implements AzureKeyVaultClientWrapper {

    private final DefaultAzureCredential credential;
    private final ConcurrentHashMap<String, KeyClient> keyClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CryptographyClient> cryptoClients = new ConcurrentHashMap<>();

    public DefaultAzureKeyVaultClientWrapper() {
        this.credential = new DefaultAzureCredentialBuilder().build();
    }

    @Override
    public KeyVaultKey getKey(final String vaultUrl, final String keyName) {
        final KeyClient keyClient = keyClients.computeIfAbsent(vaultUrl,
                url -> new KeyClientBuilder()
                        .vaultUrl(url)
                        .credential(credential)
                        .buildClient());
        return keyClient.getKey(keyName);
    }

    @Override
    public SignResult sign(final String vaultUrl, final String keyName,
            final SignatureAlgorithm algorithm, final byte[] digest) {
        final String keyIdentifier = vaultUrl + "/keys/" + keyName;
        final CryptographyClient cryptoClient = cryptoClients.computeIfAbsent(keyIdentifier,
                id -> new CryptographyClientBuilder()
                        .credential(credential)
                        .keyIdentifier(id)
                        .buildClient());
        return cryptoClient.sign(algorithm, digest);
    }
}
