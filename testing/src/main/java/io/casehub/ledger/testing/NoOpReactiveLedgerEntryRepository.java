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
import io.casehub.ledger.api.spi.ReactiveLedgerEntryRepository;
import io.smallrye.mutiny.Uni;

@Alternative
@Priority(1)
@ApplicationScoped
public class NoOpReactiveLedgerEntryRepository implements ReactiveLedgerEntryRepository {

    @Override
    public Uni<LedgerEntry> save(final LedgerEntry entry, final String tenancyId) {
        return Uni.createFrom().item(entry);
    }

    @Override
    public Uni<List<LedgerEntry>> findBySubjectId(final UUID subjectId, final String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerEntry>> findBySubjectIdAndTimeRange(
            final UUID subjectId, final Instant from, final Instant to, final String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<Optional<LedgerEntry>> findLatestBySubjectId(
            final UUID subjectId, final String tenancyId) {
        return Uni.createFrom().item(Optional.empty());
    }

    @Override
    public Uni<Optional<LedgerEntry>> findEntryById(final UUID id, final String tenancyId) {
        return Uni.createFrom().item(Optional.empty());
    }

    @Override
    public Uni<List<LedgerEntry>> findByActorId(
            final String actorId, final Instant from, final Instant to, final String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerEntry>> findByActorRole(
            final String actorRole, final Instant from, final Instant to, final String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerEntry>> findCausedBy(final UUID entryId, final String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<LedgerAttestation> saveAttestation(
            final LedgerAttestation attestation, final String tenancyId) {
        return Uni.createFrom().item(attestation);
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryId(
            final UUID entryId, final String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdAndCapabilityTag(
            final UUID entryId, final String capabilityTag, final String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByEntryIdGlobal(
            final UUID entryId, final String tenancyId) {
        return Uni.createFrom().item(List.of());
    }

    @Override
    public Uni<List<LedgerAttestation>> findAttestationsByAttestorIdAndCapabilityTag(
            final String attestorId, final String capabilityTag, final String tenancyId) {
        return Uni.createFrom().item(List.of());
    }
}
