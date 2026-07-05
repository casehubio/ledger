package io.casehub.ledger.api.spi;

import java.util.UUID;

import io.smallrye.mutiny.Uni;

import io.casehub.ledger.api.model.AuditRecord;

/**
 * Reactive counterpart to {@link LedgerAppender}.
 *
 * <p>Method signatures mirror {@link LedgerAppender} with the return type wrapped
 * in {@link Uni}. The default implementation wraps the blocking appender on the
 * Mutiny worker pool. Native async adapters activate via {@code @Alternative @Priority(N)}.
 */
public interface ReactiveLedgerAppender {

    /**
     * Append a new audit entry to the ledger.
     *
     * @param record the audit record to persist; must not be {@code null}
     * @param tenancyId the tenant scope for this entry
     * @return Uni emitting the UUID of the persisted ledger entry
     */
    Uni<UUID> append(AuditRecord record, String tenancyId);
}
