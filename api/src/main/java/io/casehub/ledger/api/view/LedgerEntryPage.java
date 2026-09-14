package io.casehub.ledger.api.view;

import java.util.List;

public record LedgerEntryPage(
        List<LedgerEntryView> entries,
        int totalCount,
        boolean hasMore) {
}
