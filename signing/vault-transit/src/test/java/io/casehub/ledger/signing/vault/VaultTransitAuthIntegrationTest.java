package io.casehub.ledger.signing.vault;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * End-to-end integration test for Vault Transit signing with AppRole authentication.
 * Simulates the full flow: AppRole login → fetch key → sign.
 */
class VaultTransitAuthIntegrationTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(0);
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

    @Test
    void appRoleLogin_thenFetchKey_thenSign() throws Exception {
        // 1. Stub AppRole login
        wireMock.stubFor(post(urlEqualTo("/v1/auth/approle/login"))
                .willReturn(okJson("{\"auth\":{\"client_token\":\"hvs.dynamic\",\"lease_duration\":3600,\"renewable\":true}}")));

        // 2. Stub key info
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        final String pemJson = pem.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/my-key"))
                .withHeader("X-Vault-Token", equalTo("hvs.dynamic"))
                .willReturn(okJson("{\"data\":{\"type\":\"ed25519\",\"keys\":{\"1\":{\"public_key\":\"" + pemJson + "\"}}}}")));

        // 3. Stub sign
        final byte[] data = "integration test data".getBytes();
        final Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(data);
        final byte[] sigBytes = sig.sign();
        wireMock.stubFor(post(urlEqualTo("/v1/transit/sign/my-key"))
                .withHeader("X-Vault-Token", equalTo("hvs.dynamic"))
                .willReturn(okJson("{\"data\":{\"signature\":\"vault:v1:" + Base64.getEncoder().encodeToString(sigBytes) + "\"}}")));

        // Execute: AppRole login → fetch key → sign
        final HttpClient http = HttpClient.newHttpClient();
        final ObjectMapper mapper = new ObjectMapper();
        final VaultTokenSource tokenSource = new AppRoleVaultTokenSource(
                wireMock.baseUrl(), "role-id", "secret-id", "approle",
                http, mapper, Clock.systemUTC());

        final VaultTransitSigningConfig config = new VaultTransitSigningConfig(
                wireMock.baseUrl(), Map.of("actor1", "my-key"));
        final VaultTransitSigningClient client = new VaultTransitSigningClient(config, http, mapper);

        final String token = tokenSource.token();
        final var publicKey = client.fetchPublicKey(token, "my-key");
        assertThat(publicKey.getEncoded()).isEqualTo(kp.getPublic().getEncoded());

        final byte[] result = client.sign(token, "my-key", data);

        final Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(result)).isTrue();
    }
}
