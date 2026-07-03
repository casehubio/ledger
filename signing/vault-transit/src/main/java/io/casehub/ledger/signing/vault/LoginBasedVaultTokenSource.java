package io.casehub.ledger.signing.vault;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class LoginBasedVaultTokenSource implements VaultTokenSource {

    private static final Logger LOG = Logger.getLogger(LoginBasedVaultTokenSource.class.getName());
    private static final int DEFAULT_BUFFER_SECONDS = 30;

    private record TokenState(String token, Instant expiresAt) {}

    private volatile TokenState state = new TokenState(null, Instant.EPOCH);

    private final String vaultAddress;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Clock clock;

    protected LoginBasedVaultTokenSource(final String vaultAddress, final HttpClient http,
            final ObjectMapper mapper, final Clock clock) {
        this.vaultAddress = Objects.requireNonNull(vaultAddress);
        this.http = Objects.requireNonNull(http);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public String token() {
        TokenState s = state;
        if (s.token() != null && !isExpired(s)) return s.token();
        synchronized (this) {
            s = state;
            if (s.token() != null && !isExpired(s)) return s.token();
            login();
            return state.token();
        }
    }

    @Override
    public void invalidate() {
        state = new TokenState(null, Instant.EPOCH);
    }

    private boolean isExpired(final TokenState s) {
        return clock.instant().isAfter(s.expiresAt());
    }

    private void login() {
        try {
            final HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddress + loginPath()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginRequestBody()))
                    .build();
            final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Vault auth login returned HTTP " + resp.statusCode()
                        + " at " + loginPath() + ": " + resp.body());
            }
            final JsonNode root = mapper.readTree(resp.body());
            final String clientToken = root.path("auth").path("client_token").asText();
            final int leaseDuration = root.path("auth").path("lease_duration").asInt();

            final int buffer = Math.min(DEFAULT_BUFFER_SECONDS, leaseDuration / 2);
            if (leaseDuration < 10) {
                LOG.warning("Vault lease_duration is very short (" + leaseDuration
                        + "s) — token will expire quickly");
            }
            final Instant expiresAt = clock.instant().plusSeconds(leaseDuration).minusSeconds(buffer);
            state = new TokenState(clientToken, expiresAt);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Vault auth login interrupted at " + loginPath(), e);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Vault auth login failed at " + loginPath(), e);
        }
    }

    protected abstract String loginPath();
    protected abstract String loginRequestBody();
}
