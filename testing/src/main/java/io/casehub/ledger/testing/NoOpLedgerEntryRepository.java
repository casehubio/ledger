package io.casehub.ledger.testing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;

@Alternative
@Priority(1)
@ApplicationScoped
public class NoOpLedgerEntryRepository implements LedgerEntryRepository {

    @Override
    public LedgerEntry save(final LedgerEntry entry, final String tenancyId) {
        return entry;
    }

    @Override
    public List<LedgerEntry> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerEntry> findBySubjectIdAndTimeRange(
            final UUID subjectId, final Instant from, final Instant to, final String tenancyId) {
        return List.of();
    }

    @Override
    public Optional<LedgerEntry> findLatestBySubjectId(final UUID subjectId, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public Optional<LedgerEntry> findEntryById(final UUID id, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryId(
            final UUID ledgerEntryId, final String tenancyId) {
        return List.of();
    }

    @Override
    public LedgerAttestation saveAttestation(final LedgerAttestation attestation, final String tenancyId) {
        return attestation;
    }

    @Override
    public List<LedgerEntry> findByActorId(
            final String actorId, final Instant from, final Instant to, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerEntry> findByActorRole(
            final String actorRole, final Instant from, final Instant to, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerEntry> findCausedBy(final UUID entryId, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByEntryIdGlobal(
            final UUID entryId, final String tenancyId) {
        return List.of();
    }

    @Override
    public List<LedgerAttestation> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag, final String tenancyId) {
        return List.of();
    }
}
