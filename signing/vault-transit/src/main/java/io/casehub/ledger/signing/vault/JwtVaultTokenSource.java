package io.casehub.ledger.signing.vault;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JwtVaultTokenSource extends LoginBasedVaultTokenSource {

    private final String role;
    private final Supplier<String> jwtSupplier;
    private final String mountPath;

    public JwtVaultTokenSource(final String vaultAddress, final String role,
            final Supplier<String> jwtSupplier, final String mountPath,
            final HttpClient http, final ObjectMapper mapper, final Clock clock) {
        super(vaultAddress, http, mapper, clock);
        this.role = Objects.requireNonNull(role);
        this.jwtSupplier = Objects.requireNonNull(jwtSupplier);
        this.mountPath = mountPath != null ? mountPath : "jwt";
    }

    public static JwtVaultTokenSource fromFile(final String vaultAddress, final String role,
            final Path jwtPath, final String mountPath,
            final HttpClient http, final ObjectMapper mapper, final Clock clock) {
        Objects.requireNonNull(jwtPath, "jwtPath must not be null");
        return new JwtVaultTokenSource(vaultAddress, role, () -> {
            try {
                return Files.readString(jwtPath).trim();
            } catch (final IOException e) {
                throw new UncheckedIOException("Failed to read JWT from " + jwtPath, e);
            }
        }, mountPath, http, mapper, clock);
    }

    @Override
    protected String loginPath() {
        return "/v1/auth/" + mountPath + "/login";
    }

    @Override
    protected String loginRequestBody() {
        final String jwt = Objects.requireNonNull(jwtSupplier.get(), "JWT supplier returned null");
        return "{\"role\":\"" + role + "\",\"jwt\":\"" + jwt + "\"}";
    }
}
