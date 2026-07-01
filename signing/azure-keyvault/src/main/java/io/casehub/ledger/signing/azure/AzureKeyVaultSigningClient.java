package io.casehub.ledger.signing.azure;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.keys.cryptography.models.SignResult;
import com.azure.security.keyvault.keys.cryptography.models.SignatureAlgorithm;
import com.azure.security.keyvault.keys.models.JsonWebKey;
import com.azure.security.keyvault.keys.models.KeyType;
import com.azure.security.keyvault.keys.models.KeyVaultKey;

/**
 * Pure Java client for Azure Key Vault signing.
 *
 * <p>The private key never leaves Azure Key Vault. Only the public key is fetched for storage on
 * ledger entries (needed by verification infrastructure). Signing happens via Key Vault API call.
 *
 * <p><strong>Stateless:</strong> This client holds no per-actor state. Caching is the
 * responsibility of the Quarkus CDI adapter ({@code AbstractCachingAgentSigner}).
 *
 * <p><strong>Algorithm support:</strong> Only EC key types are supported (P-256, P-384, P-521).
 *
 * <p><strong>Signature format:</strong> Azure Key Vault returns raw R‖S bytes. This client
 * converts to DER-encoded format using {@link EcSignatureConverter}.
 *
 * <p><strong>No casehub-ledger dependency.</strong> Usable from any framework or plain {@code main()}.
 */
public class AzureKeyVaultSigningClient {

    private static final Logger LOG = Logger.getLogger(AzureKeyVaultSigningClient.class.getName());

    private final AzureKeyVaultClientWrapper wrapper;

    public AzureKeyVaultSigningClient() {
        this.wrapper = new DefaultAzureKeyVaultClientWrapper();
    }

    public AzureKeyVaultSigningClient(final AzureKeyVaultClientWrapper wrapper) {
        this.wrapper = wrapper;
    }

    /**
     * Fetches the public key and algorithm from Azure Key Vault.
     *
     * @param keyRef key reference (format: "vaultUrl#keyName")
     * @return context with public key and algorithm, or empty if key not found or is not EC
     */
    public Optional<AzureKeyVaultContext> fetchPublicKey(final String keyRef) {
        final String[] parts = keyRef.split("#");
        if (parts.length != 2) {
            LOG.log(Level.SEVERE, "Invalid key reference format: " + keyRef
                    + " (expected vaultUrl#keyName)");
            return Optional.empty();
        }
        final String vaultUrl = parts[0];
        final String keyName = parts[1];

        try {
            final KeyVaultKey keyVaultKey = wrapper.getKey(vaultUrl, keyName);

            final JsonWebKey jwk = keyVaultKey.getKey();
            if (jwk.getKeyType() != KeyType.EC) {
                LOG.log(Level.SEVERE, "Key " + keyRef + " is " + jwk.getKeyType()
                        + " but this adapter requires EC");
                return Optional.empty();
            }

            final PublicKey publicKey = jwk.toEc().getPublic();
            if (!(publicKey instanceof ECPublicKey ecKey)) {
                LOG.log(Level.SEVERE, "Key " + keyRef + " could not be converted to ECPublicKey");
                return Optional.empty();
            }

            final SignatureAlgorithm algorithm = mapCurveToAlgorithm(ecKey);

            return Optional.of(new AzureKeyVaultContext(keyName, vaultUrl, publicKey, algorithm));
        } catch (final ResourceNotFoundException e) {
            LOG.log(Level.FINE, "Key not found in Azure Key Vault: " + keyRef);
            return Optional.empty();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to fetch public key from Azure Key Vault for key "
                    + keyRef, e);
        }
    }

    /**
     * Signs data via Azure Key Vault.
     *
     * <p>Uses the provided algorithm to compute the digest and derive the component size
     * for DER conversion. No key metadata re-fetch.
     *
     * @param vaultUrl  Azure Key Vault URL
     * @param keyName   key name
     * @param algorithm signature algorithm from cached context
     * @param data      data to sign
     * @return DER-encoded ECDSA signature bytes
     */
    public byte[] sign(final String vaultUrl, final String keyName,
                       final SignatureAlgorithm algorithm, final byte[] data) {
        try {
            final byte[] digest = computeDigest(data, algorithm);
            final int componentSize = mapAlgorithmToComponentSize(algorithm);

            final SignResult signResult = wrapper.sign(vaultUrl, keyName, algorithm, digest);
            final byte[] rawSignature = signResult.getSignature();

            return EcSignatureConverter.rawToDer(rawSignature, componentSize);
        } catch (final Exception e) {
            throw new RuntimeException("Azure Key Vault signing failed for key "
                    + vaultUrl + "#" + keyName, e);
        }
    }

    private static SignatureAlgorithm mapCurveToAlgorithm(final ECPublicKey ecKey) {
        final int orderBitLength = ecKey.getParams().getOrder().bitLength();
        return switch (orderBitLength) {
            case 256 -> SignatureAlgorithm.ES256;
            case 384 -> SignatureAlgorithm.ES384;
            case 521 -> SignatureAlgorithm.ES512;
            default -> throw new IllegalArgumentException(
                    "Unsupported EC curve for Azure Key Vault: " + orderBitLength);
        };
    }

    private static int mapAlgorithmToComponentSize(final SignatureAlgorithm algorithm) {
        if (algorithm == SignatureAlgorithm.ES256) return 32;
        if (algorithm == SignatureAlgorithm.ES384) return 48;
        if (algorithm == SignatureAlgorithm.ES512) return 66;
        throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
    }

    private static byte[] computeDigest(final byte[] data, final SignatureAlgorithm algorithm) {
        try {
            final String digestAlg;
            if (algorithm == SignatureAlgorithm.ES256) {
                digestAlg = "SHA-256";
            } else if (algorithm == SignatureAlgorithm.ES384) {
                digestAlg = "SHA-384";
            } else if (algorithm == SignatureAlgorithm.ES512) {
                digestAlg = "SHA-512";
            } else {
                throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
            }
            return MessageDigest.getInstance(digestAlg).digest(data);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to compute digest", e);
        }
    }
}
