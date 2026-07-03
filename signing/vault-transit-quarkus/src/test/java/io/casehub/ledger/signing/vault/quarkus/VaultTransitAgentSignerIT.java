package io.casehub.ledger.signing.vault.quarkus;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;

import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.casehub.ledger.runtime.service.AgentKeyMaterial;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.runtime.service.AgentSigner;
import io.casehub.ledger.signing.vault.VaultAuthenticationException;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Quarkus integration test for {@link VaultTransitAgentSigner}.
 * Uses WireMock to simulate Vault Transit REST API.
 * {@code casehub-ledger-memory} satisfies repository SPIs without a real database.
 */
@QuarkusTest
class VaultTransitAgentSignerIT {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(8098);
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
        ((VaultTransitAgentSigner) agentSigner).invalidateAll();
    }

    @Inject
    AgentSigner agentSigner;

    @Inject
    Event<AgentKeyRotatedEvent> keyRotatedEvent;

    /** Returns the public key PEM as a Java string with real newlines. */
    private static String publicKeyPem(final KeyPair kp) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                        .encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    /**
     * Builds a Vault Transit key-info JSON response with type field and multi-version keys.
     */
    private static String keyInfoResponse(final KeyPair kp) {
        final String pemJsonSafe = publicKeyPem(kp)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "{\"data\":{\"type\":\"ed25519\",\"keys\":{\"1\":{\"public_key\":\"" + pemJsonSafe + "\"}}}}";
    }

    private static String signResponse(final byte[] sigBytes) {
        return "{\"data\":{\"signature\":\"vault:v1:" +
                Base64.getEncoder().encodeToString(sigBytes) + "\"}}";
    }

    private static void stubKeyInfo(final KeyPair kp) {
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/reviewer-key"))
                .willReturn(okJson(keyInfoResponse(kp))));
    }

    private static byte[] realSign(final KeyPair kp, final byte[] data) throws Exception {
        final Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(kp.getPrivate());
        sig.update(data);
        return sig.sign();
    }

    private static void stubSign(final KeyPair kp, final byte[] data) throws Exception {
        final byte[] sigBytes = realSign(kp, data);
        wireMock.stubFor(post(urlEqualTo("/v1/transit/sign/reviewer-key"))
                .willReturn(okJson(signResponse(sigBytes))));
    }

    @Test
    void signsData_viaVaultTransitApi() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] data = "canonical ledger bytes".getBytes();
        stubKeyInfo(kp);
        stubSign(kp, data);

        final Optional<AgentSignature> result = agentSigner.sign("claude:reviewer@v1", data);

        assertThat(result).isPresent();
        assertThat(result.get().publicKey()).isEqualTo(kp.getPublic().getEncoded());

        // Round-trip: verify the signature with JCA (same verification path as AgentCryptographicVerifier)
        final Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(kp.getPublic());
        verifier.update(data);
        assertThat(verifier.verify(result.get().signature())).isTrue();
    }

    @Test
    void keyMaterial_returnsKeyWithoutCallingSignApi() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        stubKeyInfo(kp);

        final Optional<AgentKeyMaterial> result = agentSigner.keyMaterial("claude:reviewer@v1");

        assertThat(result).isPresent();
        assertThat(result.get().publicKey()).isEqualTo(kp.getPublic().getEncoded());
        assertThat(result.get().keyRef()).isNotBlank();

        // Verify NO calls to the sign API
        wireMock.verify(0, anyRequestedFor(urlEqualTo("/v1/transit/sign/reviewer-key")));
        // Exactly one call to the key info API (to load context)
        wireMock.verify(1, getRequestedFor(urlEqualTo("/v1/transit/keys/reviewer-key")));
    }

    @Test
    void keyRotationEvent_invalidatesCache() throws Exception {
        final KeyPair kpOld = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final KeyPair kpNew = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] data = "rotation test".getBytes();

        // First call — cache loads old key
        stubKeyInfo(kpOld);
        stubSign(kpOld, data);
        agentSigner.sign("claude:reviewer@v1", data);
        wireMock.verify(1, getRequestedFor(urlEqualTo("/v1/transit/keys/reviewer-key")));

        // Fire key rotation event
        wireMock.resetAll();
        stubKeyInfo(kpNew);
        stubSign(kpNew, data);
        keyRotatedEvent.fire(new AgentKeyRotatedEvent("claude:reviewer@v1", "old-ref", "new-ref"));

        // Second call — should re-fetch (cache was invalidated by the event)
        final Optional<AgentSignature> result = agentSigner.sign("claude:reviewer@v1", data);
        assertThat(result).isPresent();
        assertThat(result.get().publicKey())
                .as("Should use new key after rotation event")
                .isEqualTo(kpNew.getPublic().getEncoded());
        wireMock.verify(1, getRequestedFor(urlEqualTo("/v1/transit/keys/reviewer-key")));
    }

    @Test
    void returnsEmpty_forUnmappedActor() {
        final Optional<AgentSignature> result = agentSigner.sign("unmapped-actor", new byte[]{1});
        assertThat(result).isEmpty();
        wireMock.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void retries_onVaultAuthenticationException() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] data = "retry test".getBytes();
        stubKeyInfo(kp);

        // First sign call returns 403
        wireMock.stubFor(post(urlEqualTo("/v1/transit/sign/reviewer-key"))
                .inScenario("auth-retry")
                .whenScenarioStateIs("Started")
                .willReturn(okJson("{\"errors\":[]}").withStatus(403))
                .willSetStateTo("retried"));

        // Second sign call (after tokenSource.invalidate()) succeeds
        final byte[] sigBytes = realSign(kp, data);
        wireMock.stubFor(post(urlEqualTo("/v1/transit/sign/reviewer-key"))
                .inScenario("auth-retry")
                .whenScenarioStateIs("retried")
                .willReturn(okJson(signResponse(sigBytes))));

        final Optional<AgentSignature> result = agentSigner.sign("claude:reviewer@v1", data);

        assertThat(result).isPresent();
        assertThat(result.get().publicKey()).isEqualTo(kp.getPublic().getEncoded());
        // Verify exactly 2 sign calls (first 403, second success)
        wireMock.verify(2, anyRequestedFor(urlEqualTo("/v1/transit/sign/reviewer-key")));
    }

    @Test
    void retries_exhausted_onDouble403() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] data = "double 403 test".getBytes();
        stubKeyInfo(kp);

        // Both sign calls return 403
        wireMock.stubFor(post(urlEqualTo("/v1/transit/sign/reviewer-key"))
                .willReturn(okJson("{\"errors\":[]}").withStatus(403)));

        // Second 403 throws VaultAuthenticationException (not caught by adapter)
        assertThatThrownBy(() -> agentSigner.sign("claude:reviewer@v1", data))
                .isInstanceOf(VaultAuthenticationException.class)
                .hasMessageContaining("Vault authentication failed (HTTP 403)");

        // Verify exactly 2 sign calls (first 403 + retry 403)
        wireMock.verify(2, anyRequestedFor(urlEqualTo("/v1/transit/sign/reviewer-key")));
    }

    @Test
    void retries_onFetchPublicKey403() throws Exception {
        final KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] data = "key fetch retry test".getBytes();

        // First key fetch returns 403
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/reviewer-key"))
                .inScenario("key-retry")
                .whenScenarioStateIs("Started")
                .willReturn(okJson("{\"errors\":[]}").withStatus(403))
                .willSetStateTo("retried"));

        // Second key fetch (after tokenSource.invalidate()) succeeds
        wireMock.stubFor(get(urlEqualTo("/v1/transit/keys/reviewer-key"))
                .inScenario("key-retry")
                .whenScenarioStateIs("retried")
                .willReturn(okJson(keyInfoResponse(kp))));

        stubSign(kp, data);

        final Optional<AgentSignature> result = agentSigner.sign("claude:reviewer@v1", data);

        assertThat(result).isPresent();
        assertThat(result.get().publicKey()).isEqualTo(kp.getPublic().getEncoded());
        // Verify exactly 2 key fetch calls (first 403, second success)
        wireMock.verify(2, getRequestedFor(urlEqualTo("/v1/transit/keys/reviewer-key")));
    }
}
