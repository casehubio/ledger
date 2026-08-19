package io.casehub.ledger.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.annotations.Audited;
import io.casehub.ledger.annotations.Attested;
import io.casehub.ledger.annotations.ConfidenceScore;
import io.casehub.ledger.annotations.SubjectId;
import io.casehub.ledger.annotations.ActorId;
import io.casehub.ledger.annotations.TenancyId;
import io.casehub.ledger.annotations.Verdict;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.extension.RegisterExtension;

class AttestedTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(AttestedService.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.db-kind=h2\n"
                            + "quarkus.datasource.jdbc.url=jdbc:h2:mem:attestedtestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1\n"
                            + "quarkus.datasource.username=sa\n"
                            + "quarkus.datasource.password=\n"
                            + "quarkus.hibernate-orm.database.generation=none\n"
                            + "quarkus.flyway.migrate-at-start=true\n"
                            + "quarkus.flyway.locations=classpath:db/ledger/migration\n"
                            + "casehub.ledger.outcome.default-attestor-id=test-attestor\n"),
                            "application.properties"));

    @Inject
    AttestedService service;

    @Inject
    LedgerEntryRepository repo;

    @Test
    void attestedCreatesEntryAndAttestation() {
        UUID subjectId = UUID.randomUUID();
        service.review(subjectId, "reviewer-1", "default");

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.EVENT);

        var attestations = repo.findAttestationsByEntryId(entries.get(0).id, "default");
        assertThat(attestations).hasSize(1);
        assertThat(attestations.get(0).verdict).isEqualTo(AttestationVerdict.SOUND);
        assertThat(attestations.get(0).confidence).isEqualTo(0.9);
        assertThat(attestations.get(0).capabilityTag).isEqualTo("review");
    }

    @Test
    void dynamicVerdictOverridesStatic() {
        UUID subjectId = UUID.randomUUID();
        service.dynamicReview(subjectId, "reviewer-1", "default",
                AttestationVerdict.FLAGGED, 0.3);

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);

        var attestations = repo.findAttestationsByEntryId(entries.get(0).id, "default");
        assertThat(attestations).hasSize(1);
        assertThat(attestations.get(0).verdict).isEqualTo(AttestationVerdict.FLAGGED);
        assertThat(attestations.get(0).confidence).isEqualTo(0.3);
    }

    @Test
    void attestedWithCommandEntryType() {
        UUID subjectId = UUID.randomUUID();
        service.commandReview(subjectId, "reviewer-1", "default");

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.COMMAND);
    }

    @Test
    void failureOnAttestedFallsBackToAuditOnly() {
        UUID subjectId = UUID.randomUUID();
        assertThatThrownBy(() ->
                service.failingReview(subjectId, "reviewer-1", "default"))
                .isInstanceOf(RuntimeException.class);

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.COMMAND);

        var attestations = repo.findAttestationsByEntryId(entries.get(0).id, "default");
        assertThat(attestations).isEmpty();
    }

    @ApplicationScoped
    public static class AttestedService {

        @Audited
        @Attested(verdict = AttestationVerdict.SOUND, confidence = 0.9,
                  capabilityTag = "review")
        public String review(@SubjectId UUID caseId, @ActorId String actorId,
                             @TenancyId String tenancyId) {
            return "reviewed";
        }

        @Audited
        @Attested(capabilityTag = "review")
        public String dynamicReview(@SubjectId UUID caseId, @ActorId String actorId,
                                    @TenancyId String tenancyId,
                                    @Verdict AttestationVerdict verdict,
                                    @ConfidenceScore double confidence) {
            return "reviewed";
        }

        @Audited(entryType = LedgerEntryType.COMMAND)
        @Attested(verdict = AttestationVerdict.SOUND, confidence = 0.9,
                  capabilityTag = "review")
        public String commandReview(@SubjectId UUID caseId, @ActorId String actorId,
                                    @TenancyId String tenancyId) {
            return "reviewed";
        }

        @Audited(auditFailures = true)
        @Attested(verdict = AttestationVerdict.SOUND, confidence = 0.9,
                  capabilityTag = "review")
        public String failingReview(@SubjectId UUID caseId, @ActorId String actorId,
                                    @TenancyId String tenancyId) {
            throw new RuntimeException("review failed");
        }
    }
}
