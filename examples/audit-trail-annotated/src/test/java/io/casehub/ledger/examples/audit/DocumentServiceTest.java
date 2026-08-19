package io.casehub.ledger.examples.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DocumentServiceTest {

    @Inject
    DocumentService service;

    @Inject
    LedgerEntryRepository repo;

    @Test
    void uploadCreatesEventEntry() {
        UUID docId = UUID.randomUUID();
        service.upload(docId, "alice", "Design Doc", "content...");

        var entries = repo.findBySubjectId(docId, TenancyConstants.DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).actorId).isEqualTo("alice");
        assertThat(entries.get(0).actorRole).isEqualTo("Author");
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.EVENT);
    }

    @Test
    void submitForReviewCreatesCommandEntry() {
        UUID docId = UUID.randomUUID();
        service.submitForReview(docId, "alice");

        var entries = repo.findBySubjectId(docId, TenancyConstants.DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.COMMAND);
    }

    @Test
    void approveRecordsReviewerIdentity() {
        UUID docId = UUID.randomUUID();
        service.approve(docId, "bob-reviewer");

        var entries = repo.findBySubjectId(docId, TenancyConstants.DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).actorId).isEqualTo("bob-reviewer");
        assertThat(entries.get(0).actorRole).isEqualTo("Reviewer");
    }

    @Test
    void multipleOperationsBuildAuditTrail() {
        UUID docId = UUID.randomUUID();
        service.upload(docId, "alice", "Spec", "v1");
        service.submitForReview(docId, "alice");
        service.approve(docId, "bob");

        var entries = repo.findBySubjectId(docId, TenancyConstants.DEFAULT_TENANT_ID);
        assertThat(entries).hasSize(3);
    }

    @Test
    void archiveWorksWithExplicitTenancyId() {
        UUID docId = UUID.randomUUID();
        service.archive(docId, "system:archiver", "tenant-42");

        var entries = repo.findBySubjectId(docId, "tenant-42");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).actorId).isEqualTo("system:archiver");
    }
}
