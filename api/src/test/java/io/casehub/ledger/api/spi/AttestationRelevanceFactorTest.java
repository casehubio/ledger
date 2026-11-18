package io.casehub.ledger.api.spi;

import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttestationRelevanceFactorTest {

    private static AttestationRelevanceContext ctx(UUID attestationId) {
        return new AttestationRelevanceContext(
                attestationId, "attestor", ActorType.AGENT, null,
                "actor", "tenant", null, null, Instant.now(), null);
    }

    @Test
    void assessBatch_defaultDelegatesToAssess() {
        AttestationRelevanceFactor factor = new AttestationRelevanceFactor() {
            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                return new RelevanceAssessment(0.7, "test");
            }

            @Override
            public String name() {
                return "test-factor";
            }
        };

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        var contexts = List.of(ctx(id1), ctx(id2));

        Map<UUID, RelevanceAssessment> results = factor.assessBatch(contexts);

        assertEquals(2, results.size());
        assertEquals(0.7, results.get(id1).weight());
        assertEquals(0.7, results.get(id2).weight());
    }

    @Test
    void name_returnsIdentifier() {
        AttestationRelevanceFactor factor = new AttestationRelevanceFactor() {
            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                return RelevanceAssessment.NEUTRAL;
            }

            @Override
            public String name() {
                return "org-proximity";
            }
        };

        assertEquals("org-proximity", factor.name());
    }
}
