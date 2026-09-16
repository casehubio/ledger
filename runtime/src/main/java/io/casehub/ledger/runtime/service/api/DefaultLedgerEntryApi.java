package io.casehub.ledger.runtime.service.api;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PathParam;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.ledger.api.view.AppendEntryRequest;
import io.casehub.ledger.api.view.LedgerEntryPage;
import io.casehub.ledger.api.view.LedgerEntryView;
import io.casehub.platform.api.identity.TenancyConstants;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@McpDomain(value = "ledger/entries", basePath = "/api/v1/ledger")
@ApplicationScoped
public class DefaultLedgerEntryApi {

    @Inject LedgerEntryRepository repository;
    @Inject LedgerAppender appender;

    @PlatformQuery("List ledger entries by subject or actor with optional time range")
    public LedgerEntryPage listEntries(UUID subjectId, String actorId,
                                        @ContextParam("tenancyId") String tenancyId,
                                        Instant from, Instant to,
                                        Integer offset, Integer limit) {
        final String tid = defaultTenancyId(tenancyId);
        final int off = offset != null ? offset : 0;
        final int lim = limit != null ? limit : 20;

        final List<? extends LedgerEntry> entries;
        if (subjectId != null) {
            entries = (from != null && to != null)
                    ? repository.findBySubjectIdAndTimeRange(subjectId, from, to, tid)
                    : repository.findBySubjectId(subjectId, tid);
        } else if (actorId != null) {
            final Instant start = from != null ? from : Instant.EPOCH;
            final Instant end = to != null ? to : Instant.now();
            entries = repository.findByActorId(actorId, start, end, tid);
        } else {
            entries = List.of();
        }

        final List<LedgerEntryView> all = entries.stream()
                .map(DefaultLedgerEntryApi::toView).toList();
        final int total = all.size();
        final int endIdx = Math.min(off + lim, total);
        final List<LedgerEntryView> items = off < total
                ? all.subList(off, endIdx) : List.of();
        return new LedgerEntryPage(items, total, endIdx < total);
    }

    @PlatformQuery("Get a single ledger entry by ID")
    public LedgerEntryView getEntry(@PathParam UUID id,
                                     @ContextParam("tenancyId") String tenancyId) {
        return repository.findEntryById(id, defaultTenancyId(tenancyId))
                .map(DefaultLedgerEntryApi::toView)
                .orElse(null);
    }

    @PlatformQuery("Get entries causally triggered by this entry")
    public List<LedgerEntryView> getCausedBy(@PathParam UUID id,
                                              @ContextParam("tenancyId") String tenancyId) {
        return repository.findCausedBy(id, defaultTenancyId(tenancyId))
                .stream().map(DefaultLedgerEntryApi::toView).toList();
    }

    @PlatformMutation("Append a new audit entry to the ledger")
    public LedgerEntryView appendEntry(AppendEntryRequest request,
                                        @ContextParam("tenancyId") String tenancyId) {
        final String tid = defaultTenancyId(tenancyId);
        AuditRecord record = AuditRecord.event(request.actorId(), request.subjectId());
        if (request.actorRole() != null) {
            record = record.withActorRole(request.actorRole());
        }
        if (request.metadata() != null) {
            record = record.withMetadata(request.metadata());
        }
        if (request.domainData() != null) {
            record = record.withDomainData(request.domainData());
        }
        final UUID entryId = appender.append(record, tid);
        return repository.findEntryById(entryId, tid)
                .map(DefaultLedgerEntryApi::toView)
                .orElseThrow();
    }

    static LedgerEntryView toView(final LedgerEntry e) {
        return new LedgerEntryView(
                e.id, e.subjectId, e.tenancyId, e.sequenceNumber,
                e.entryType != null ? e.entryType.name() : null,
                e.actorId,
                e.actorType != null ? e.actorType.name() : null,
                e.actorRole, e.occurredAt, e.digest, e.traceId,
                e.causedByEntryId, e.metadata, e.domainData);
    }

    static String defaultTenancyId(final String tenancyId) {
        return tenancyId != null ? tenancyId : TenancyConstants.DEFAULT_TENANT_ID;
    }
}
