package io.casehub.ledger.signing.vault;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

class JwtVaultTokenSourceTest {

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
        return "{\"auth\":{\"client_token\":\"" + token
                + "\",\"lease_duration\":" + leaseDuration + ",\"renewable\":true}}";
    }

    @Test
    void token_logsInWithSupplierJwt() {
        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.jwt-token", 3600))));

        final var source = new JwtVaultTokenSource(
                wireMock.baseUrl(), "my-role", () -> "eyJhbGciOiJSUzI1NiJ9.test",
                "jwt", http, mapper, Clock.systemUTC());

        assertThat(source.token()).isEqualTo("hvs.jwt-token");
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/auth/jwt/login"))
                .withRequestBody(containing("eyJhbGciOiJSUzI1NiJ9.test"))
                .withRequestBody(containing("my-role")));
    }

    @Test
    void token_fromFile_readsJwtFromFile() throws IOException {
        final Path jwtFile = tempDir.resolve("token.jwt");
        Files.writeString(jwtFile, "eyJhbGciOiJSUzI1NiJ9.from-file");

        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.file-token", 3600))));

        final var source = JwtVaultTokenSource.fromFile(
                wireMock.baseUrl(), "my-role", jwtFile, "jwt",
                http, mapper, Clock.systemUTC());

        assertThat(source.token()).isEqualTo("hvs.file-token");
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/auth/jwt/login"))
                .withRequestBody(containing("eyJhbGciOiJSUzI1NiJ9.from-file")));
    }

    @Test
    void token_returnsCachedWithinTTL() {
        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.cached", 3600))));

        final Instant now = Instant.parse("2026-07-03T12:00:00Z");
        final var source = new JwtVaultTokenSource(
                wireMock.baseUrl(), "my-role", () -> "jwt-value",
                "jwt", http, mapper, Clock.fixed(now, ZoneOffset.UTC));

        assertThat(source.token()).isEqualTo("hvs.cached");
        assertThat(source.token()).isEqualTo("hvs.cached");
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/auth/jwt/login")));
    }

    @Test
    void token_reLoginsAfterInvalidate() {
        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.original", 3600))));

        final var source = new JwtVaultTokenSource(
                wireMock.baseUrl(), "my-role", () -> "jwt-value",
                "jwt", http, mapper, Clock.systemUTC());

        assertThat(source.token()).isEqualTo("hvs.original");

        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.refreshed", 3600))));

        source.invalidate();
        assertThat(source.token()).isEqualTo("hvs.refreshed");
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/auth/jwt/login")));
    }

    @Test
    void token_throwsOnLoginFailure() {
        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(aResponse().withStatus(403)));

        final var source = new JwtVaultTokenSource(
                wireMock.baseUrl(), "my-role", () -> "bad-jwt",
                "jwt", http, mapper, Clock.systemUTC());

        assertThatThrownBy(source::token)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("403");
    }

    @Test
    void token_usesCustomMountPath() {
        wireMock.stubFor(post(urlEqualTo("/v1/auth/oidc/login"))
                .willReturn(okJson(loginResponse("hvs.oidc", 3600))));

        final var source = new JwtVaultTokenSource(
                wireMock.baseUrl(), "my-role", () -> "jwt-value",
                "oidc", http, mapper, Clock.systemUTC());

        assertThat(source.token()).isEqualTo("hvs.oidc");
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/auth/oidc/login")));
    }

    @Test
    void token_fromFile_throwsOnMissingFile() {
        final Path missing = tempDir.resolve("nonexistent");

        final var source = JwtVaultTokenSource.fromFile(
                wireMock.baseUrl(), "my-role", missing, "jwt",
                http, mapper, Clock.systemUTC());

        assertThatThrownBy(source::token)
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void token_supplierCalledOnEachLogin() {
        final AtomicInteger callCount = new AtomicInteger();
        final Supplier<String> counting = () -> {
            callCount.incrementAndGet();
            return "jwt-v" + callCount.get();
        };

        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.first", 3600))));

        final var source = new JwtVaultTokenSource(
                wireMock.baseUrl(), "my-role", counting,
                "jwt", http, mapper, Clock.systemUTC());

        source.token();
        assertThat(callCount.get()).isEqualTo(1);

        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.second", 3600))));

        source.invalidate();
        source.token();
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void token_supplierReturningNull_throwsNPE() {
        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.token", 3600))));

        final var source = new JwtVaultTokenSource(
                wireMock.baseUrl(), "my-role", () -> null,
                "jwt", http, mapper, Clock.systemUTC());

        assertThatThrownBy(source::token)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("JWT supplier returned null");
    }

    @Test
    void token_fromFile_kubernetesMountPath() throws IOException {
        final Path jwtFile = tempDir.resolve("sa-token");
        Files.writeString(jwtFile, "eyJhbGciOiJSUzI1NiJ9.k8s-jwt");

        wireMock.stubFor(post(urlEqualTo("/v1/auth/kubernetes/login"))
                .willReturn(okJson(loginResponse("hvs.k8s", 3600))));

        final var source = JwtVaultTokenSource.fromFile(
                wireMock.baseUrl(), "my-role", jwtFile, "kubernetes",
                http, mapper, Clock.systemUTC());

        assertThat(source.token()).isEqualTo("hvs.k8s");
        wireMock.verify(postRequestedFor(urlEqualTo("/v1/auth/kubernetes/login"))
                .withRequestBody(containing("eyJhbGciOiJSUzI1NiJ9.k8s-jwt")));
    }

    @Test
    void token_fromFile_reReadsOnReLogin() throws IOException {
        final Path jwtFile = tempDir.resolve("rotating-token");
        Files.writeString(jwtFile, "jwt-v1");

        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.first", 3600))));

        final var source = JwtVaultTokenSource.fromFile(
                wireMock.baseUrl(), "my-role", jwtFile, "jwt",
                http, mapper, Clock.systemUTC());

        source.token();

        Files.writeString(jwtFile, "jwt-v2");
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.second", 3600))));

        source.invalidate();
        source.token();

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/auth/jwt/login"))
                .withRequestBody(containing("jwt-v2")));
    }

    @Test
    void token_defaultsMountPathToJwt() {
        wireMock.stubFor(post(urlEqualTo("/v1/auth/jwt/login"))
                .willReturn(okJson(loginResponse("hvs.default", 3600))));

        final var source = new JwtVaultTokenSource(
                wireMock.baseUrl(), "my-role", () -> "jwt-value",
                null, http, mapper, Clock.systemUTC());

        assertThat(source.token()).isEqualTo("hvs.default");
        wireMock.verify(1, postRequestedFor(urlEqualTo("/v1/auth/jwt/login")));
    }
}
