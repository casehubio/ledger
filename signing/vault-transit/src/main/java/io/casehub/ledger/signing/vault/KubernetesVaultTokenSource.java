package io.casehub.ledger.signing.vault;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class KubernetesVaultTokenSource extends LoginBasedVaultTokenSource {

    private final String role;
    private final Path jwtPath;
    private final String mountPath;

    public KubernetesVaultTokenSource(final String vaultAddress, final String role,
            final Path jwtPath, final String mountPath,
            final HttpClient http, final ObjectMapper mapper, final Clock clock) {
        super(vaultAddress, http, mapper, clock);
        this.role = Objects.requireNonNull(role);
        this.jwtPath = Objects.requireNonNull(jwtPath);
        this.mountPath = mountPath != null ? mountPath : "kubernetes";
    }

    @Override
    protected String loginPath() {
        return "/v1/auth/" + mountPath + "/login";
    }

    @Override
    protected String loginRequestBody() {
        try {
            final String jwt = Files.readString(jwtPath).trim();
            return "{\"role\":\"" + role + "\",\"jwt\":\"" + jwt + "\"}";
        } catch (final IOException e) {
            throw new RuntimeException("Failed to read Kubernetes JWT from " + jwtPath, e);
        }
    }
}
