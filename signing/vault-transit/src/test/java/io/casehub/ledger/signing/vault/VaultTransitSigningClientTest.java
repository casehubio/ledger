package io.casehub.ledger.signing.vault;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Pure Java unit test for {@link VaultTransitSigningClient}.
 * Uses WireMock to simulate the Vault Transit REST API. No CDI, no Quarkus.
 */
class VaultTransitSigningClientTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(0); // random port
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    private VaultTransitSigningClient createClient() {
        final VaultTransitSigningConfig config = new VaultTransitSigningConfig(
                wireMock.baseUrl(), Map.of("actor1", "my-key"));
        return new VaultTransitSigningClient(config, HttpClient.newHttpClient(), new ObjectMapper());
    }

    /** Returns the public key PEM as a Java string with real newlines. */
    private static String publicKeyPem(final KeyPair kp) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                        .encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * Builds a Vault Transit key-info JSON with a single version.
     * Newlines in the PEM are escaped as \n (JSON escape).
     */
    private static String keyInfoResponse(final KeyPair kp) {
        return keyInfoResponse(kp, "1");
    }

    private static String keyInfoResponse(final KeyPair kp, final String version) {
        final String pemJsonSafe = publicKeyPem(kp)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "{\"data\":{\"type\":\"ed25519\",\"keys\":{\""
                + version + "\":{\"public_key\":\"" + pemJsonSafe + "\"}}}}";
    }

    /**
     * Builds a multi-version key-info response. The caller provides version→KeyPair pairs.
     * This simulates a Vault Transit key that has been rotated.
     */
    private static String multiVersionKeyInfoResponse(final Map<String, KeyPair> versionKeys) {
        final StringBuilder keysJson = new StringBuilder("{\"data\":{\"type\":\"ed25519\",\"keys\":{");
        boolean first = true;
        for (final Map.Entry<String, KeyPair> entry : versionKeys.entrySet()) {
            if (!first) keysJson.append(",");
            first = false;
            final String pemJsonSafe = publicKeyPem(entry.getValue())
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n");
            keysJson.append("\"").append(entry.getKey())
                    .append("\":{\"public_key\":\"").append(pemJsonSafe).append("\"}");
        }
        keysJson.append("}}}");
        return keysJson.toString();
    }

    private static String signResponse(final byte[] sigBytes) {
        return "{\"data\":{\"signature\":\"vault:v1:" +
                Base64.getEncoder().encodeToString(sigBytes) + "\"}}";
    }

    private static byte[] realSign(final KeyPair kp, final byte[] data) throws Exception {
        final Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(data);
        return sig.sign();
    }

    // --- Sign tests ---

    @Test
    void sign_returnsDecodedSignatureBytes() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] data = "canonical ledger bytes".getBytes();
        final byte[] sigBytes = realSign(kp, data);

        wireMock.stubFor(post(urlEqualTo("/v1/transit/sign/my-key"))
                .withHeader("X-Vault-Token", equalTo("test-token"))
                .willReturn(okJson(signResponse(sigBytes))));

        final byte[] result = createClient().sign("test-token", "my-key", data);

        assertThat(result).isEqualTo(sigBytes);

        // Verify the signature with JCA
        final Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(result)).isTrue();
    }

    @Test
    void sign_throwsVaultAuthenticationExceptionOn403() {
        wireMock.stubFor(post(urlEqualTo("/v1/transit/sign/my-key"))
                .willReturn(forbidden()));

        assertThatThrownBy(() -> createClient().sign("test-token", "my-key", new byte[]{1}))
                .isInstanceOf(VaultAuthenticationException.class)
                .hasMessageContaining("HTTP 403");
    }

    // --- FetchPublicKey tests ---

    @Test
    void fetchPublicKey_returnsParsedEd25519Key() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/my-key"))
                .withHeader("X-Vault-Token", equalTo("test-token"))
                .willReturn(okJson(keyInfoResponse(kp))));

        final PublicKey result = createClient().fetchPublicKey("test-token", "my-key");

        assertThat(result.getEncoded()).isEqualTo(kp.getPublic().getEncoded());
        assertThat(result.getAlgorithm()).isIn("Ed25519", "EdDSA");
    }

    @Test
    void fetchPublicKey_selectsHighestVersion() throws Exception {
        // Bug fix regression test: existing code used keys.fields().next() which returns
        // version 1 (oldest). After rotation, Vault signs with the latest version.
        final KeyPair kpV1 = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final KeyPair kpV2 = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final KeyPair kpV3 = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        // Response includes versions 1, 2, 3 — client must select version 3
        final Map<String, KeyPair> versionKeys = Map.of(
                "1", kpV1, "2", kpV2, "3", kpV3);
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/my-key"))
                .willReturn(okJson(multiVersionKeyInfoResponse(versionKeys))));

        final PublicKey result = createClient().fetchPublicKey("test-token", "my-key");

        assertThat(result.getEncoded())
                .as("Should select version 3 (highest), not version 1 (oldest)")
                .isEqualTo(kpV3.getPublic().getEncoded());
    }

    @Test
    void fetchPublicKey_throwsOnKeyNotFound() {
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/missing-key"))
                .willReturn(notFound()));

        assertThatThrownBy(() -> createClient().fetchPublicKey("test-token", "missing-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void fetchPublicKey_throwsVaultAuthenticationExceptionOn403() {
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/my-key"))
                .willReturn(forbidden()));

        assertThatThrownBy(() -> createClient().fetchPublicKey("test-token", "my-key"))
                .isInstanceOf(VaultAuthenticationException.class)
                .hasMessageContaining("HTTP 403");
    }

    @Test
    void fetchPublicKey_rejectsNonEd25519KeyType() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final String pemJsonSafe = publicKeyPem(kp)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        final String rsaKeyResponse = "{\"data\":{\"type\":\"rsa-2048\",\"keys\":{\"1\":{\"public_key\":\""
                + pemJsonSafe + "\"}}}}";

        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/my-key"))
                .willReturn(okJson(rsaKeyResponse)));

        assertThatThrownBy(() -> createClient().fetchPublicKey("test-token", "my-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("rsa-2048")
                .hasMessageContaining("requires ed25519");
    }

    @Test
    void fetchPublicKey_rejectsEcdsaKeyType() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final String pemJsonSafe = publicKeyPem(kp)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        final String ecKeyResponse = "{\"data\":{\"type\":\"ecdsa-p256\",\"keys\":{\"1\":{\"public_key\":\""
                + pemJsonSafe + "\"}}}}";

        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/my-key"))
                .willReturn(okJson(ecKeyResponse)));

        assertThatThrownBy(() -> createClient().fetchPublicKey("test-token", "my-key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ecdsa-p256")
                .hasMessageContaining("requires ed25519");
    }
}
