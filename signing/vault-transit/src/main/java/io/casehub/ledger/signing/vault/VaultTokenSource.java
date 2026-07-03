package io.casehub.ledger.signing.vault;

public interface VaultTokenSource {
    String token();
    void invalidate();
}
