package io.casehub.ledger.rest;

public class LedgerNotFoundException extends RuntimeException {

    public LedgerNotFoundException(final String message) {
        super(message);
    }
}
