package io.casehub.ledger.signing.vault;

public class VaultAuthenticationException extends RuntimeException {

    public VaultAuthenticationException(final String message) {
        super(message);
    }
}
