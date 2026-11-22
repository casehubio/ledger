package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.spi.AttestationRelevanceContext;
import io.casehub.ledger.api.spi.AttestationRelevanceFactor;
import io.casehub.ledger.api.spi.RelevanceAssessment;
import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelevanceComposerTest {

    private static AttestationRelevanceContext ctx(UUID attestationId) {
        return new AttestationRelevanceContext(
                attestationId, "attestor", ActorType.AGENT, null,
                "actor", "tenant", null, null, Instant.now(), null);
    }

    @Test
    void noFactors_allNeutral() {
        var composer = new RelevanceComposer(List.of(), 0.5);
        UUID id = UUID.randomUUID();
        var result = composer.assess(List.of(ctx(id)));
        assertEquals(1.0, result.get(id));
    }

    @Test
    void singleFactor_returnsFactorWeight() {
        AttestationRelevanceFactor factor = new AttestationRelevanceFactor() {
            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                return new RelevanceAssessment(0.7, "test");
            }

            @Override
            public String name() {
                return "test";
            }
        };
        var composer = new RelevanceComposer(List.of(factor), 0.5);
        UUID id = UUID.randomUUID();
        var result = composer.assess(List.of(ctx(id)));
        assertEquals(0.7, result.get(id), 1e-9);
    }

    @Test
    void twoFactors_productsFloored() {
        AttestationRelevanceFactor f1 = fixedFactor(0.6, "f1");
        AttestationRelevanceFactor f2 = fixedFactor(0.7, "f2");
        var composer = new RelevanceComposer(List.of(f1, f2), 0.5);
        UUID id = UUID.randomUUID();
        var result = composer.assess(List.of(ctx(id)));
        assertEquals(0.5, result.get(id), 1e-9);
    }

    @Test
    void twoFactors_productAboveFloor_notFloored() {
        AttestationRelevanceFactor f1 = fixedFactor(0.8, "f1");
        AttestationRelevanceFactor f2 = fixedFactor(0.9, "f2");
        var composer = new RelevanceComposer(List.of(f1, f2), 0.5);
        UUID id = UUID.randomUUID();
        var result = composer.assess(List.of(ctx(id)));
        assertEquals(0.72, result.get(id), 1e-9);
    }

    @Test
    void neutralFactor_treatedAsNeutral() {
        AttestationRelevanceFactor factor = fixedFactor(1.0, "noop");
        var composer = new RelevanceComposer(List.of(factor), 0.5);
        UUID id = UUID.randomUUID();
        var result = composer.assess(List.of(ctx(id)));
        assertEquals(1.0, result.get(id), 1e-9);
    }

    @Test
    void multipleAttestations_eachAssessedIndependently() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        AttestationRelevanceFactor factor = new AttestationRelevanceFactor() {
            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                return ctx.attestationId().equals(id1)
                        ? new RelevanceAssessment(0.7, "close")
                        : new RelevanceAssessment(0.9, "far");
            }

            @Override
            public String name() {
                return "variable";
            }
        };
        var composer = new RelevanceComposer(List.of(factor), 0.5);
        var result = composer.assess(List.of(ctx(id1), ctx(id2)));
        assertEquals(0.7, result.get(id1), 1e-9);
        assertEquals(0.9, result.get(id2), 1e-9);
    }

    @Test
    void floorValidation_zeroRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelevanceComposer(List.of(), 0.0));
    }

    @Test
    void floorValidation_aboveOneRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RelevanceComposer(List.of(), 1.01));
    }

    @Test
    void floorValidation_oneAccepted() {
        var composer = new RelevanceComposer(List.of(), 1.0);
        assertNotNull(composer);
    }


    @Test
    void batchFactor_composesWithPerItemFactor() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        AttestationRelevanceFactor perItem = fixedFactor(0.8, "per-item");

        AttestationRelevanceFactor batchFactor = new AttestationRelevanceFactor() {
            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                return RelevanceAssessment.NEUTRAL;
            }

            @Override
            public Map<UUID, RelevanceAssessment> assessBatch(List<AttestationRelevanceContext> ctxs) {
                Map<UUID, RelevanceAssessment> result = new java.util.LinkedHashMap<>();
                for (AttestationRelevanceContext ctx : ctxs) {
                    if (ctx.attestationId().equals(id1)) {
                        result.put(ctx.attestationId(), new RelevanceAssessment(0.5, "capped"));
                    } else {
                        result.put(ctx.attestationId(), RelevanceAssessment.NEUTRAL);
                    }
                }
                return result;
            }

            @Override
            public String name() {
                return "batch";
            }
        };

        var composer = new RelevanceComposer(List.of(perItem, batchFactor), 0.3);
        var result   = composer.assess(List.of(ctx(id1), ctx(id2)));

        // id1: 0.8 * 0.5 = 0.4
        assertEquals(0.4, result.get(id1), 1e-9);
        // id2: 0.8 * 1.0 = 0.8
        assertEquals(0.8, result.get(id2), 1e-9);
    }

    @Test
    void batchFactor_partialMap_missingIdTreatedAsNeutral() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        AttestationRelevanceFactor batchFactor = new AttestationRelevanceFactor() {
            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                return RelevanceAssessment.NEUTRAL;
            }

            @Override
            public Map<UUID, RelevanceAssessment> assessBatch(List<AttestationRelevanceContext> ctxs) {
                // Only return assessment for id1, omit id2
                return Map.of(id1, new RelevanceAssessment(0.6, "partial"));
            }

            @Override
            public String name() {
                return "partial";
            }
        };

        var composer = new RelevanceComposer(List.of(batchFactor), 0.5);
        var result   = composer.assess(List.of(ctx(id1), ctx(id2)));

        assertEquals(0.6, result.get(id1), 1e-9);
        // id2 not in batch result -> stays at initial 1.0
        assertEquals(1.0, result.get(id2), 1e-9);
    }


    private static AttestationRelevanceFactor fixedFactor(double weight, String name) {
        return new AttestationRelevanceFactor() {
            @Override
            public RelevanceAssessment assess(AttestationRelevanceContext ctx) {
                return new RelevanceAssessment(weight, name);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}
