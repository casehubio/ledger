package io.casehub.ledger.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.OutcomeRecorder;
import io.casehub.ledger.graphql.dto.AppendLedgerEntryInput;
import io.casehub.ledger.graphql.dto.CreateAttestationInput;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LedgerMutationResolverTest {

    private LedgerMutationResolver resolver;
    private LedgerAppender ledgerAppender;
    private LedgerEntryRepository entryRepository;
    private OutcomeRecorder outcomeRecorder;
    private CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new LedgerMutationResolver();
        ledgerAppender = mock(LedgerAppender.class);
        entryRepository = mock(LedgerEntryRepository.class);
        outcomeRecorder = mock(OutcomeRecorder.class);
        currentPrincipal = mock(CurrentPrincipal.class);

        inject(resolver, "ledgerAppender", ledgerAppender);
        inject(resolver, "entryRepository", entryRepository);
        inject(resolver, "outcomeRecorder", outcomeRecorder);
        inject(resolver, "currentPrincipal", currentPrincipal);

        when(currentPrincipal.tenancyId()).thenReturn("tenant-1");
        when(currentPrincipal.actorId()).thenReturn("current-actor");
    }

    @Test
    void appendLedgerEntry_withExplicitActor() {
        UUID subjectId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        var input = new AppendLedgerEntryInput(subjectId, "custom-actor", "Resolver", null, null, null);

        when(ledgerAppender.append(any(AuditRecord.class), eq("tenant-1"))).thenReturn(entryId);

        LedgerEntry persisted = new LedgerEntry() {};
        persisted.id = entryId;
        persisted.subjectId = subjectId;
        persisted.tenancyId = "tenant-1";
        persisted.entryType = LedgerEntryType.EVENT;
        persisted.actorId = "custom-actor";
        persisted.actorType = ActorType.AGENT;
        persisted.occurredAt = Instant.now();
        when(entryRepository.findEntryById(entryId, "tenant-1")).thenReturn(Optional.of(persisted));

        var result = resolver.appendLedgerEntry(input);

        assertThat(result.id()).isEqualTo(entryId);
        assertThat(result.subjectId()).isEqualTo(subjectId);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(ledgerAppender).append(captor.capture(), eq("tenant-1"));
        assertThat(captor.getValue().actorId()).isEqualTo("custom-actor");
        assertThat(captor.getValue().actorRole()).isEqualTo("Resolver");
    }

    @Test
    void appendLedgerEntry_fallsBackToCurrentPrincipal() {
        UUID subjectId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        var input = new AppendLedgerEntryInput(subjectId, null, null, null, null, null);

        when(ledgerAppender.append(any(AuditRecord.class), eq("tenant-1"))).thenReturn(entryId);

        LedgerEntry persisted = new LedgerEntry() {};
        persisted.id = entryId;
        persisted.subjectId = subjectId;
        persisted.tenancyId = "tenant-1";
        persisted.entryType = LedgerEntryType.EVENT;
        persisted.actorId = "current-actor";
        persisted.actorType = ActorType.AGENT;
        persisted.occurredAt = Instant.now();
        when(entryRepository.findEntryById(entryId, "tenant-1")).thenReturn(Optional.of(persisted));

        resolver.appendLedgerEntry(input);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(ledgerAppender).append(captor.capture(), eq("tenant-1"));
        assertThat(captor.getValue().actorId()).isEqualTo("current-actor");
    }

    @Test
    void createAttestation_delegatesToOutcomeRecorder() {
        UUID entryId = UUID.randomUUID();
        var input = new CreateAttestationInput(entryId, "ENDORSED", 0.9, "routing");

        boolean result = resolver.createAttestation(input);

        assertThat(result).isTrue();
        verify(outcomeRecorder).addAttestation(
                entryId, AttestationVerdict.ENDORSED, 0.9, "routing");
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
