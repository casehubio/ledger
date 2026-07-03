package io.casehub.ledger.signing.vault;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

class KubernetesVaultTokenSourceTest {

    static WireMockServer wireMock;
    static final ObjectMapper mapper = new ObjectMapper();
    static final HttpClient http = HttpClient.newHttpClient();

    @TempDir
    Path tempDir;

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

    private static String loginResponse(final String token, final int leaseDuration) {
        return "{\"auth\":{\"client_token\":\"" + token + "\",\"lease_duration\":" + leaseDuration + ",\"renewable\":true}}";
    }

    @Test
    void token_readsJwtFromFileAndLogsIn() throws IOException {
        final Path jwtFile = tempDir.resolve("token");
        Files.writeString(jwtFile, "eyJhbGciOiJSUzI1NiJ9.k8s-jwt-payload");

        wireMock.stubFor(post(urlEqualTo("/v1/auth/kubernetes/login"))
                .willReturn(okJson(loginResponse("hvs.k8s-token", 3600))));

        final var source = new KubernetesVaultTokenSource(
                wireMock.baseUrl(), "my-role", jwtFile, "kubernetes",
                http, mapper, Clock.systemUTC());

        assertThat(source.token()).isEqualTo("hvs.k8s-token");
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/auth/kubernetes/login"))
                .withRequestBody(containing("eyJhbGciOiJSUzI1NiJ9.k8s-jwt-payload")));
    }

    @Test
    void token_reReadsJwtOnReLogin() throws IOException {
        final Path jwtFile = tempDir.resolve("token");
        Files.writeString(jwtFile, "jwt-v1");

        wireMock.stubFor(post(urlEqualTo("/v1/auth/kubernetes/login"))
                .willReturn(okJson(loginResponse("hvs.first", 3600))));

        final var source = new KubernetesVaultTokenSource(
                wireMock.baseUrl(), "my-role", jwtFile, "kubernetes",
                http, mapper, Clock.systemUTC());

        source.token();

        // Rotate JWT file and invalidate
        Files.writeString(jwtFile, "jwt-v2");
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/v1/auth/kubernetes/login"))
                .willReturn(okJson(loginResponse("hvs.second", 3600))));

        source.invalidate();
        source.token();

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/auth/kubernetes/login"))
                .withRequestBody(containing("jwt-v2")));
    }

    @Test
    void token_throwsOnMissingJwtFile() {
        final Path missing = tempDir.resolve("nonexistent");

        final var source = new KubernetesVaultTokenSource(
                wireMock.baseUrl(), "my-role", missing, "kubernetes",
                http, mapper, Clock.systemUTC());

        assertThatThrownBy(source::token)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("nonexistent");
    }
}
