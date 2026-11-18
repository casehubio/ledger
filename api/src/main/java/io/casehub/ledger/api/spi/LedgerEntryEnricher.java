package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.model.LedgerEntry;

public interface LedgerEntryEnricher {

    void enrich(LedgerEntry entry);

    default int priority() {
        return Integer.MAX_VALUE;
    }

    /**
     * Short identifier for diagnostics, e.g. {@code "trace-id"}.
     */
    String name();


}
