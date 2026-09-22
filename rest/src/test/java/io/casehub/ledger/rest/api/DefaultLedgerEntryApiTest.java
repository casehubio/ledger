package io.casehub.ledger.rest.api;

import io.casehub.ledger.api.view.LedgerEntryPage;
import io.casehub.ledger.api.view.LedgerEntryView;
import io.casehub.ledger.core.repository.NoOpLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultLedgerEntryApiTest {

    private DefaultLedgerEntryApi api;

    @BeforeEach
    void setUp() {
        api = new DefaultLedgerEntryApi();
        api.repository = new NoOpLedgerEntryRepository();
    }

    @Test
    void listEntriesReturnsEmptyPageWhenNoEntries() {
        final UUID subjectId = UUID.randomUUID();
        final LedgerEntryPage page = api.listEntries(
                subjectId, null, null, null, null, null, null);
        assertThat(page.entries()).isEmpty();
        assertThat(page.totalCount()).isZero();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void listEntriesReturnsEmptyWhenNeitherSubjectNorActor() {
        final LedgerEntryPage page = api.listEntries(
                null, null, null, null, null, null, null);
        assertThat(page.entries()).isEmpty();
    }

    @Test
    void getEntryReturnsNullForMissingEntry() {
        final LedgerEntryView entry = api.getEntry(UUID.randomUUID(), null);
        assertThat(entry).isNull();
    }

    @Test
    void getCausedByReturnsEmptyForMissingEntry() {
        final var result = api.getCausedBy(UUID.randomUUID(), null);
        assertThat(result).isEmpty();
    }

    @Test
    void defaultTenancyIdReturnsDefaultWhenNull() {
        assertThat(DefaultLedgerEntryApi.defaultTenancyId(null)).isNotNull();
    }

    @Test
    void defaultTenancyIdPreservesExplicitValue() {
        assertThat(DefaultLedgerEntryApi.defaultTenancyId("my-tenant"))
                .isEqualTo("my-tenant");
    }
}
