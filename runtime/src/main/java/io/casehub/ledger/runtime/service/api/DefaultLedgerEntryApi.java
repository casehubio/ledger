package io.casehub.ledger.runtime.service.api;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.LedgerEntryApi;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.view.AppendEntryRequest;
import io.casehub.ledger.api.view.LedgerEntryPage;
import io.casehub.ledger.api.view.LedgerEntryView;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@DefaultBean
@ApplicationScoped
public class DefaultLedgerEntryApi implements LedgerEntryApi {

    @Inject LedgerEntryRepository repository;
    @Inject LedgerAppender appender;

    @Override
    public LedgerEntryPage listEntries(final UUID subjectId, final String actorId,
                                        final String tenancyId, final Instant from,
                                        final Instant to, final Integer offset,
                                        final Integer limit) {
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

    @Override
    public LedgerEntryView getEntry(final UUID id, final String tenancyId) {
        return repository.findEntryById(id, defaultTenancyId(tenancyId))
                .map(DefaultLedgerEntryApi::toView)
                .orElse(null);
    }

    @Override
    public List<LedgerEntryView> getCausedBy(final UUID id, final String tenancyId) {
        return repository.findCausedBy(id, defaultTenancyId(tenancyId))
                .stream().map(DefaultLedgerEntryApi::toView).toList();
    }

    @Override
    public LedgerEntryView appendEntry(final AppendEntryRequest request,
                                        final String tenancyId) {
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
