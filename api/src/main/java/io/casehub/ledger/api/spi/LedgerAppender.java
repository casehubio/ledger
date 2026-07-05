package io.casehub.ledger.api.spi;

import java.util.UUID;

import io.casehub.ledger.api.model.AuditRecord;

/**
 * Value-type write SPI for appending audit entries to the ledger.
 *
 * <p>Accepts an {@link AuditRecord} — a plain value type with no JPA dependency —
 * and returns the UUID of the persisted ledger entry. The implementation creates
 * the appropriate {@code LedgerEntry} subclass and delegates to the save pipeline
 * (sequence assignment, hash chain, enrichment).
 *
 * <p>This is the primary write path for api-tier consumers that need to record
 * events and commands without depending on runtime internals. For combined
 * entry + attestation writes, use {@link OutcomeRecorder} instead.
 *
 * <p>ATTESTATION entries are not supported via this path — {@link AuditRecord}
 * rejects them at construction time.
 */
public interface LedgerAppender {

    /**
     * Append a new audit entry to the ledger.
     *
     * @param record the audit record to persist; must not be {@code null}
     * @param tenancyId the tenant scope for this entry
     * @return the UUID of the persisted ledger entry
     */
    UUID append(AuditRecord record, String tenancyId);
}
