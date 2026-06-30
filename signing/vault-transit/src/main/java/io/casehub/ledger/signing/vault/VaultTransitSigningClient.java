package io.casehub.ledger.signing.vault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Pure Java client for HashiCorp Vault Transit Secrets Engine signing.
 *
 * <p>The private key never leaves Vault. Only the public key is fetched for storage on
 * ledger entries (needed by verification infrastructure). Signing happens via REST API call.
 *
 * <p><strong>Auth:</strong> Uses a static Vault token. Production deployments should use
 * AppRole or OIDC.
 *
 * <p><strong>Algorithm support:</strong> Only {@code ed25519} Vault Transit key types are
 * supported. Vault returns a 64-byte raw signature prefixed with {@code vault:v1:} in
 * base64. This client strips the prefix and decodes to raw bytes.
 *
 * <p><strong>No casehub-ledger dependency.</strong> PEM parsing is inline (strip headers,
 * base64-decode, {@code KeyFactory.getInstance("Ed25519")}). Usable from any framework
 * or plain {@code main()}.
 */
public class VaultTransitSigningClient {

    private static final String VAULT_V1_PREFIX = "vault:v1:";
    private static final String PEM_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_END = "-----END PUBLIC KEY-----";

    private final String address;
    private final String token;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public VaultTransitSigningClient(final VaultTransitSigningConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        this.address = config.address();
        this.token = config.token();
        this.http = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    // Visible for testing — allows injecting a custom HttpClient
    VaultTransitSigningClient(final VaultTransitSigningConfig config, final HttpClient httpClient) {
        Objects.requireNonNull(config, "config must not be null");
        this.address = config.address();
        this.token = config.token();
        this.http = httpClient;
        this.mapper = new ObjectMapper();
    }

    /**
     * Fetches the current public key from Vault Transit.
     *
     * <p>Calls {@code GET /v1/transit/keys/<keyName>} and parses the response.
     * Selects the <strong>highest-numbered version</strong> from the {@code data.keys} map
     * (the latest key after any rotations).
     *
     * <p>Validates that the key type is {@code ed25519}. Non-ed25519 types are rejected
     * with a {@link RuntimeException}.
     *
     * @param keyName Vault Transit key name
     * @return the Ed25519 public key
     * @throws RuntimeException if the key is not found (HTTP 404), auth fails (HTTP 403),
     *                          the key type is not ed25519, or the response is malformed
     */
    public PublicKey fetchPublicKey(final String keyName) {
        try {
            final HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(address + "/v1/transit/keys/" + keyName))
                    .header("X-Vault-Token", token)
                    .GET()
                    .build();
            final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Vault returned HTTP " + resp.statusCode()
                        + " fetching key info for " + keyName + ": " + resp.body());
            }
            final JsonNode root = mapper.readTree(resp.body());

            // Validate key type — only ed25519 is supported
            final String keyType = root.path("data").path("type").asText();
            if (!"ed25519".equals(keyType)) {
                throw new RuntimeException("Key " + keyName + " is " + keyType
                        + " but this adapter requires ed25519");
            }

            // keys is a map of version number (string) → key info;
            // select the highest-numbered version (latest after rotation)
            final JsonNode keys = root.path("data").path("keys");
            final JsonNode latestKey = selectLatestVersion(keys, keyName);
            final String pem = latestKey.path("public_key").asText();
            return parseEd25519PublicKeyPem(pem);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to fetch public key from Vault for key " + keyName, e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to fetch public key from Vault for key " + keyName, e);
        }
    }

    /**
     * Signs data via Vault Transit.
     *
     * <p>Calls {@code POST /v1/transit/sign/<keyName>} with the base64-encoded data.
     * Returns the raw signature bytes (strips the {@code vault:v1:} prefix and base64-decodes).
     *
     * @param keyName Vault Transit key name
     * @param data    data to sign
     * @return raw Ed25519 signature bytes (64 bytes)
     * @throws RuntimeException on Vault API errors or unexpected response format
     */
    public byte[] sign(final String keyName, final byte[] data) {
        try {
            return callVaultSign(keyName, data);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Vault Transit signing interrupted for key " + keyName, e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Vault Transit signing failed for key " + keyName, e);
        }
    }

    private byte[] callVaultSign(final String keyName, final byte[] data)
            throws IOException, InterruptedException {
        final String inputB64 = Base64.getEncoder().encodeToString(data);
        final String body = "{\"input\":\"" + inputB64 + "\"}";
        final HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(address + "/v1/transit/sign/" + keyName))
                .header("X-Vault-Token", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Vault Transit sign returned HTTP " + resp.statusCode()
                    + " for key " + keyName + ": " + resp.body());
        }
        final JsonNode root = mapper.readTree(resp.body());
        final String vaultSig = root.path("data").path("signature").asText();
        if (!vaultSig.startsWith(VAULT_V1_PREFIX)) {
            throw new RuntimeException("Unexpected Vault signature format: " + vaultSig);
        }
        return Base64.getDecoder().decode(vaultSig.substring(VAULT_V1_PREFIX.length()));
    }

    /**
     * Selects the highest-numbered version from the Vault Transit {@code data.keys} map.
     *
     * <p>Vault's keys map uses string-encoded integer version numbers as field names
     * (e.g. {@code "1"}, {@code "2"}, {@code "3"}). After key rotation, the signing key
     * is the latest version. This method iterates all entries and selects the one with
     * the highest integer key.
     */
    private static JsonNode selectLatestVersion(final JsonNode keys, final String keyName) {
        int highestVersion = -1;
        JsonNode latestKey = null;
        final Iterator<Map.Entry<String, JsonNode>> fields = keys.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> entry = fields.next();
            final int version = Integer.parseInt(entry.getKey());
            if (version > highestVersion) {
                highestVersion = version;
                latestKey = entry.getValue();
            }
        }
        if (latestKey == null) {
            throw new RuntimeException("No key versions found in Vault Transit response for key " + keyName);
        }
        return latestKey;
    }

    /**
     * Parses an Ed25519 public key from PEM format.
     *
     * <p>Inline PEM parsing: strips headers, base64-decodes, and feeds to
     * {@code KeyFactory.getInstance("Ed25519")}. No dependency on LedgerPemUtil —
     * pure Java modules have no casehub-ledger dependency.
     */
    private static PublicKey parseEd25519PublicKeyPem(final String pem) {
        try {
            final String base64 = pem
                    .replace(PEM_BEGIN, "")
                    .replace(PEM_END, "")
                    .replaceAll("\\s+", "");
            final byte[] derBytes = Base64.getDecoder().decode(base64);
            final X509EncodedKeySpec spec = new X509EncodedKeySpec(derBytes);
            return KeyFactory.getInstance("Ed25519").generatePublic(spec);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to parse Ed25519 public key PEM", e);
        }
    }
}
