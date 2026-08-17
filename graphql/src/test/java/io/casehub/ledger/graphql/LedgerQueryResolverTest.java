package io.casehub.ledger.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.api.spi.TrustScoreSource;
import io.casehub.ledger.graphql.dto.LedgerEntryFilterInput;
import io.casehub.ledger.graphql.dto.LedgerEntryPage;
import io.casehub.ledger.graphql.dto.TrustRoutingProfileType;
import io.casehub.ledger.graphql.dto.TrustScoreType;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.platform.api.identity.ActorType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.graphql.PageInput;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerQueryResolverTest {

    private LedgerQueryResolver resolver;
    private LedgerEntryRepository entryRepository;
    private TrustScoreSource trustScoreSource;
    private LedgerVerificationService verificationService;
    private CurrentPrincipal currentPrincipal;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new LedgerQueryResolver();
        entryRepository = mock(LedgerEntryRepository.class);
        trustScoreSource = mock(TrustScoreSource.class);
        verificationService = mock(LedgerVerificationService.class);
        currentPrincipal = mock(CurrentPrincipal.class);

        inject(resolver, "entryRepository", entryRepository);
        inject(resolver, "trustScoreSource", trustScoreSource);
        inject(resolver, "verificationService", verificationService);
        inject(resolver, "currentPrincipal", currentPrincipal);

        when(currentPrincipal.tenancyId()).thenReturn("tenant-1");
    }

    @Test
    void ledgerEntries_bySubjectId_returnsPaginatedResults() {
        UUID subjectId = UUID.randomUUID();
        LedgerEntry entry = createEntry(subjectId);
        when(entryRepository.findBySubjectId(subjectId, "tenant-1"))
                .thenReturn(List.of(entry));

        var filter = new LedgerEntryFilterInput(subjectId, null, null, null, null);
        LedgerEntryPage result = resolver.ledgerEntries(filter, new PageInput(0, 10, null));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).subjectId()).isEqualTo(subjectId);
        assertThat(result.pageInfo().totalCount()).isEqualTo(1);
    }

    @Test
    void ledgerEntries_withTimeRange_filtersCorrectly() {
        UUID subjectId = UUID.randomUUID();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-31T23:59:59Z");
        when(entryRepository.findBySubjectIdAndTimeRange(subjectId, from, to, "tenant-1"))
                .thenReturn(List.of());

        var filter = new LedgerEntryFilterInput(subjectId, null, null, from, to);
        LedgerEntryPage result = resolver.ledgerEntries(filter, null);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void ledgerEntries_noFilter_returnsEmpty() {
        LedgerEntryPage result = resolver.ledgerEntries(null, null);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void ledgerEntry_found_returnsEntry() {
        UUID id = UUID.randomUUID();
        LedgerEntry entry = createEntry(UUID.randomUUID());
        entry.id = id;
        when(entryRepository.findEntryById(id, "tenant-1")).thenReturn(Optional.of(entry));

        var result = resolver.ledgerEntry(id);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void ledgerEntry_notFound_returnsNull() {
        UUID id = UUID.randomUUID();
        when(entryRepository.findEntryById(id, "tenant-1")).thenReturn(Optional.empty());

        assertThat(resolver.ledgerEntry(id)).isNull();
    }

    @Test
    void trustScore_returnsGlobalAndCapabilityScores() {
        when(trustScoreSource.globalScore("actor-1")).thenReturn(OptionalDouble.of(0.85));
        when(trustScoreSource.allCapabilityScores("actor-1"))
                .thenReturn(Map.of("routing", 0.9));
        when(trustScoreSource.allDimensionScores("actor-1"))
                .thenReturn(Map.of("accuracy", 0.7));

        TrustScoreType result = resolver.trustScore("actor-1");

        assertThat(result.actorId()).isEqualTo("actor-1");
        assertThat(result.globalScore()).isEqualTo(0.85);
        assertThat(result.capabilityScores()).containsEntry("routing", 0.9);
        assertThat(result.dimensionScores()).containsEntry("accuracy", 0.7);
    }

    @Test
    void trustRoutingProfile_compositeQuery() {
        when(trustScoreSource.globalScore("actor-1")).thenReturn(OptionalDouble.of(0.8));
        when(trustScoreSource.capabilityScore("actor-1", "routing"))
                .thenReturn(OptionalDouble.of(0.9));
        when(trustScoreSource.decisionCount("actor-1", "routing")).thenReturn(42);
        when(trustScoreSource.qualityScores("actor-1", "routing"))
                .thenReturn(Map.of("accuracy", 0.95, "timeliness", 0.8));

        TrustRoutingProfileType result = resolver.trustRoutingProfile("actor-1", "routing");

        assertThat(result.actorId()).isEqualTo("actor-1");
        assertThat(result.capabilityTag()).isEqualTo("routing");
        assertThat(result.globalScore()).isEqualTo(0.8);
        assertThat(result.capabilityScore()).isEqualTo(0.9);
        assertThat(result.decisionCount()).isEqualTo(42);
        assertThat(result.qualityDimensions()).containsKeys("accuracy", "timeliness");
    }

    @Test
    void merkleVerification_verified() {
        UUID subjectId = UUID.randomUUID();
        when(verificationService.verify(subjectId, "tenant-1")).thenReturn(true);
        when(verificationService.treeRoot(subjectId, "tenant-1")).thenReturn("abc123");

        var result = resolver.merkleVerification(subjectId);

        assertThat(result.verified()).isTrue();
        assertThat(result.treeRoot()).isEqualTo("abc123");
    }

    @Test
    void merkleVerification_failed() {
        UUID subjectId = UUID.randomUUID();
        when(verificationService.verify(subjectId, "tenant-1")).thenReturn(false);

        var result = resolver.merkleVerification(subjectId);

        assertThat(result.verified()).isFalse();
        assertThat(result.treeRoot()).isNull();
    }

    private static LedgerEntry createEntry(UUID subjectId) {
        LedgerEntry entry = new LedgerEntry() {};
        entry.id = UUID.randomUUID();
        entry.subjectId = subjectId;
        entry.tenancyId = "tenant-1";
        entry.sequenceNumber = 1;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-1";
        entry.actorType = ActorType.AGENT;
        entry.occurredAt = Instant.now();
        return entry;
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
