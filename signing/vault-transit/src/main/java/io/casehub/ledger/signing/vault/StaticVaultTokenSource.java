package io.casehub.ledger.signing.vault;

import java.util.Objects;

public final class StaticVaultTokenSource implements VaultTokenSource {

    private final String token;

    public StaticVaultTokenSource(final String token) {
        this.token = Objects.requireNonNull(token, "token must not be null");
    }

    @Override
    public String token() {
        return token;
    }

    @Override
    public void invalidate() {
        // Static tokens cannot be refreshed
    }
}
