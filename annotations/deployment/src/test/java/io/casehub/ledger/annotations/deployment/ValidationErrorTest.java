package io.casehub.ledger.annotations.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.casehub.ledger.annotations.Audited;
import io.casehub.ledger.annotations.Attested;
import io.casehub.ledger.annotations.ComplianceSupplement;
import io.casehub.ledger.annotations.SubjectId;
import io.casehub.ledger.annotations.ActorId;
import io.casehub.ledger.api.model.AttestationVerdict;
import io.quarkus.test.QuarkusUnitTest;

class ValidationErrorTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(NoSubjectIdService.class))
            .assertException(t ->
                    assertThat(t.getMessage()).contains("@SubjectId"));

    @Test
    void buildFailsWithMissingSubjectId() {
    }

    @ApplicationScoped
    public static class NoSubjectIdService {
        @Audited
        public void doWork(UUID id) {}
    }
}
