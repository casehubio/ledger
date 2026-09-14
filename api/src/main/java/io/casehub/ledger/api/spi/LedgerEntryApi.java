package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.view.AppendEntryRequest;
import io.casehub.ledger.api.view.LedgerEntryPage;
import io.casehub.ledger.api.view.LedgerEntryView;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@McpDomain("ledger/entries")
public interface LedgerEntryApi {

    @PlatformQuery("List ledger entries by subject or actor with optional time range")
    LedgerEntryPage listEntries(UUID subjectId, String actorId,
                                 String tenancyId, Instant from, Instant to,
                                 Integer offset, Integer limit);

    @PlatformQuery("Get a single ledger entry by ID")
    LedgerEntryView getEntry(@PathParam UUID id, String tenancyId);

    @PlatformQuery("Get entries causally triggered by this entry")
    List<LedgerEntryView> getCausedBy(@PathParam UUID id, String tenancyId);

    @PlatformMutation("Append a new audit entry to the ledger")
    LedgerEntryView appendEntry(AppendEntryRequest request, String tenancyId);
}
