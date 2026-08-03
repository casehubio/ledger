package io.casehub.ledger.api.spi;

import java.util.UUID;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.OutcomeRecord;

public interface OutcomeRecorder {

    /**
     * Write an {@link OutcomeRecord} as both a {@code LedgerEntry} (EVENT) and a
     * {@code LedgerAttestation}. Both writes commit in the same transaction.
     * For JPA consumers, both are visible in the database before this method returns.
     *
     * @return the UUID of the created {@code LedgerEntry}, for use with {@link #addAttestation}
     * @throws IllegalStateException if {@code record.attestorId()} is null and
     *                               {@code casehub.ledger.outcome.default-attestor-id} is not configured
     */
    UUID record(OutcomeRecord record);

    /**
     * Append an attestation to an existing decision entry.
     * The entry must exist; {@code subjectId} is inherited from the entry.
     * The attestor is resolved from the configured default.
     *
     * @param entryId       the LedgerEntry ID returned by {@link #record}
     * @param verdict       ENDORSED / CHALLENGED / SOUND / FLAGGED
     * @param confidence    weight in (0.0, 1.0]
     * @param capabilityTag scopes the attestation to a capability
     * @throws IllegalArgumentException if the entry does not exist
     * @throws IllegalStateException    if the default attestor is not configured
     */
    void addAttestation(UUID entryId, AttestationVerdict verdict, double confidence, String capabilityTag);
}
