package io.casehub.ledger.signing.azure;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Objects;
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
 * <p><strong>Auth:</strong> Uses {@code DefaultAzureCredential} (env vars, managed identity, Azure CLI).
 *
 * <p><strong>Algorithm support:</strong> Only EC key types are supported (P-256, P-384, P-521).
 * RSA key types are rejected at {@code fetchPublicKey()} time (returns empty, cached as absent).
 *
 * <p><strong>Signature format:</strong> Azure Key Vault returns raw R‖S bytes. This client
 * converts to DER-encoded format using {@link EcSignatureConverter} so verification infrastructure
 * (which expects DER) works correctly.
 *
 * <p><strong>No casehub-ledger dependency.</strong> Usable from any framework or plain {@code main()}.
 */
public class AzureKeyVaultSigningClient {

    private static final Logger LOG = Logger.getLogger(AzureKeyVaultSigningClient.class.getName());

    private final AzureKeyVaultClientWrapper wrapper;

    public AzureKeyVaultSigningClient(final AzureKeyVaultSigningConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.wrapper = new DefaultAzureKeyVaultClientWrapper();
    }

    // Visible for testing — allows injecting a mocked wrapper
    public AzureKeyVaultSigningClient(final AzureKeyVaultSigningConfig config,
            final AzureKeyVaultClientWrapper wrapper) {
        Objects.requireNonNull(config, "config must not be null");
        this.wrapper = wrapper;
    }

    /**
     * Fetches the public key from Azure Key Vault.
     *
     * <p>Calls {@code getKey()} and extracts the EC public key from the JsonWebKey.
     * Validates that the key type is EC — only P-256, P-384, and P-521 are supported.
     * RSA key types are rejected (logged at ERROR, return empty).
     *
     * <p>Also computes the component size (R and S byte length) from the curve parameters.
     *
     * @param keyRef key reference (format: "vaultUrl#keyName")
     * @return context with public key and component size, or empty if key not found or is not EC
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

            // Validate key type — only EC is supported
            final JsonWebKey jwk = keyVaultKey.getKey();
            if (jwk.getKeyType() != KeyType.EC) {
                LOG.log(Level.SEVERE, "Key " + keyRef + " is " + jwk.getKeyType()
                        + " but this adapter requires EC");
                return Optional.empty();
            }

            // Convert to JCA ECPublicKey
            final PublicKey publicKey = jwk.toEc().getPublic();
            if (!(publicKey instanceof ECPublicKey ecKey)) {
                LOG.log(Level.SEVERE, "Key " + keyRef + " could not be converted to ECPublicKey");
                return Optional.empty();
            }

            // Compute component size from curve
            final int componentSize = computeComponentSize(ecKey);

            return Optional.of(new AzureKeyVaultContext(keyName, vaultUrl, publicKey, componentSize));
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
     * <p>Computes the appropriate digest (SHA-256, SHA-384, or SHA-512 based on curve),
     * calls {@code sign()} with the matching algorithm (ES256, ES384, ES512), and
     * converts the raw R‖S signature to DER format using {@link EcSignatureConverter}.
     *
     * @param keyRef key reference (format: "vaultUrl#keyName")
     * @param data   data to sign
     * @return DER-encoded ECDSA signature bytes
     * @throws RuntimeException on Key Vault API errors
     */
    public byte[] sign(final String keyRef, final byte[] data) {
        final String[] parts = keyRef.split("#");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid key reference format: " + keyRef);
        }
        final String vaultUrl = parts[0];
        final String keyName = parts[1];

        try {
            // Fetch key to determine curve and algorithm
            final KeyVaultKey keyVaultKey = wrapper.getKey(vaultUrl, keyName);
            final JsonWebKey jwk = keyVaultKey.getKey();
            final PublicKey publicKey = jwk.toEc().getPublic();

            if (!(publicKey instanceof ECPublicKey ecKey)) {
                throw new IllegalStateException("Key is not an EC key");
            }

            // Determine algorithm and digest from curve
            final int componentSize = computeComponentSize(ecKey);
            final SignatureAlgorithm algorithm = mapCurveToAlgorithm(ecKey);
            final byte[] digest = computeDigest(data, algorithm);

            // Sign with Azure Key Vault — returns raw R||S
            final SignResult signResult = wrapper.sign(vaultUrl, keyName, algorithm, digest);
            final byte[] rawSignature = signResult.getSignature();

            // Convert raw R||S to DER
            return EcSignatureConverter.rawToDer(rawSignature, componentSize);
        } catch (final Exception e) {
            throw new RuntimeException("Azure Key Vault signing failed for key " + keyRef, e);
        }
    }

    private static int computeComponentSize(final ECPublicKey ecKey) {
        final int orderBitLength = ecKey.getParams().getOrder().bitLength();
        return switch (orderBitLength) {
            case 256 -> 32;
            case 384 -> 48;
            case 521 -> 66;
            default -> throw new IllegalArgumentException(
                    "Unsupported EC curve order: " + orderBitLength);
        };
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
            final MessageDigest md = MessageDigest.getInstance(digestAlg);
            return md.digest(data);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to compute digest", e);
        }
    }
}
