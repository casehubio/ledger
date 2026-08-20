package io.casehub.ledger.annotations.deployment;

import io.casehub.ledger.annotations.ActorId;
import io.casehub.ledger.annotations.Audited;
import io.casehub.ledger.annotations.SubjectId;
import io.casehub.ledger.annotations.TenancyId;
import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditedInterceptorTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(AuditedService.class, ResultItem.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.db-kind=h2\n"
                            + "quarkus.datasource.jdbc.url=jdbc:h2:mem:auditedinterceptortestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1\n"
                            + "quarkus.datasource.username=sa\n"
                            + "quarkus.datasource.password=\n"
                            + "quarkus.hibernate-orm.database.generation=none\n"
                            + "quarkus.flyway.migrate-at-start=true\n"
                            + "quarkus.flyway.locations=classpath:db/ledger/migration\n"
                            + "casehub.ledger.outcome.default-attestor-id=test-attestor\n"),
                            "application.properties"));

    @Inject
    AuditedService service;

    @Inject
    LedgerEntryRepository repo;

    @Test
    void auditedMethodCreatesEntry() {
        UUID subjectId = UUID.randomUUID();
        service.doWork(subjectId, "agent-1", "default");

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).actorId).isEqualTo("agent-1");
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.EVENT);
    }

    @Test
    void auditedMethodWithCommandEntryType() {
        UUID subjectId = UUID.randomUUID();
        service.doCommand(subjectId, "agent-1", "default");

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.COMMAND);
    }

    @Test
    void auditedMethodWithActorRole() {
        UUID subjectId = UUID.randomUUID();
        service.doWorkWithRole(subjectId, "agent-1", "default");

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).actorRole).isEqualTo("reviewer");
    }

    @Test
    void auditedMethodDoesNotCreateEntryOnException() {
        UUID subjectId = UUID.randomUUID();
        assertThatThrownBy(() -> service.doFailingWork(subjectId, "agent-1", "default"))
                .isInstanceOf(RuntimeException.class);

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).isEmpty();
    }

    @Test
    void auditFailuresCreatesEntryOnException() {
        UUID subjectId = UUID.randomUUID();
        assertThatThrownBy(() -> service.doFailingWorkWithAudit(subjectId, "agent-1", "default"))
                .isInstanceOf(RuntimeException.class);

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).entryType).isEqualTo(LedgerEntryType.COMMAND);
    }

    @Test
    void auditedMethodPopulatesDomainDataFromReturnValue() {
        UUID subjectId = UUID.randomUUID();
        service.doWorkWithResult(subjectId, "agent-1", "default");

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).domainData).isNotNull();
        assertThat(entries.get(0).domainData).containsEntry("name", "test-item");
        assertThat(entries.get(0).domainData).containsEntry("value", 42);
    }

    @Test
    void auditedVoidMethodHasNullDomainData() {
        UUID subjectId = UUID.randomUUID();
        service.doVoidWork(subjectId, "agent-1", "default");

        var entries = repo.findBySubjectId(subjectId, "default");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).domainData).isNull();
    }


    @ApplicationScoped
    public static class AuditedService {

        @Audited
        public String doWork(@SubjectId UUID subjectId, @ActorId String actorId,
                             @TenancyId String tenancyId) {
            return "done";
        }

        @Audited(entryType = LedgerEntryType.COMMAND)
        public String doCommand(@SubjectId UUID subjectId, @ActorId String actorId,
                                @TenancyId String tenancyId) {
            return "commanded";
        }

        @Audited(actorRole = "reviewer")
        public String doWorkWithRole(@SubjectId UUID subjectId, @ActorId String actorId,
                                     @TenancyId String tenancyId) {
            return "reviewed";
        }

        @Audited
        public String doFailingWork(@SubjectId UUID subjectId, @ActorId String actorId,
                                    @TenancyId String tenancyId) {
            throw new RuntimeException("boom");
        }

        @Audited(auditFailures = true)
        public String doFailingWorkWithAudit(@SubjectId UUID subjectId, @ActorId String actorId,
                                             @TenancyId String tenancyId) {
            throw new RuntimeException("boom");
        }

        @Audited
        public ResultItem doWorkWithResult(@SubjectId UUID subjectId, @ActorId String actorId,
                                           @TenancyId String tenancyId) {
            return new ResultItem("test-item", 42);
        }

        @Audited
        public void doVoidWork(@SubjectId UUID subjectId, @ActorId String actorId,
                               @TenancyId String tenancyId) {
            // void — no return value
        }
    }

    public record ResultItem(String name, int value) {}

}
