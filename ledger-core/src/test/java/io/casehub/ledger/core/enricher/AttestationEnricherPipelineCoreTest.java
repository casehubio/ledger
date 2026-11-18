package io.casehub.ledger.core.enricher;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.api.spi.LedgerAttestationEnricher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttestationEnricherPipelineCoreTest {

    private static class TestEntry extends LedgerEntry {}

    @Test
    void noEnrichers_clearsSnapshotOnly() {
        var pipeline = new AttestationEnricherPipelineCore(List.of());
        var att = new LedgerAttestation();
        att.relevanceSnapshot = "{\"caller-supplied\":true}";
        pipeline.enrich(att, new TestEntry());
        assertNull(att.relevanceSnapshot);
    }

    @Test
    void singleEnricher_writesSnapshot() {
        LedgerAttestationEnricher enricher = new LedgerAttestationEnricher() {
            @Override
            public void enrich(LedgerAttestation attestation, LedgerEntry entry) {
                attestation.relevanceSnapshot = "{\"org-distance\":2}";
            }

            @Override
            public String name() {
                return "test";
            }
        };

        var pipeline = new AttestationEnricherPipelineCore(List.of(enricher));
        var att = new LedgerAttestation();
        pipeline.enrich(att, new TestEntry());
        assertEquals("{\"org-distance\":2}", att.relevanceSnapshot);
    }

    @Test
    void callerSuppliedSnapshot_cleared() {
        LedgerAttestationEnricher enricher = new LedgerAttestationEnricher() {
            @Override
            public void enrich(LedgerAttestation attestation, LedgerEntry entry) {
                assertNull(attestation.relevanceSnapshot);
            }

            @Override
            public String name() {
                return "verifier";
            }
        };

        var pipeline = new AttestationEnricherPipelineCore(List.of(enricher));
        var att = new LedgerAttestation();
        att.relevanceSnapshot = "{\"caller-supplied\":true}";
        pipeline.enrich(att, new TestEntry());
    }

    @Test
    void failingEnricher_nonFatal_attestationStillUsable() {
        LedgerAttestationEnricher failing = new LedgerAttestationEnricher() {
            @Override
            public void enrich(LedgerAttestation attestation, LedgerEntry entry) {
                throw new RuntimeException("boom");
            }

            @Override
            public String name() {
                return "failing";
            }
        };

        LedgerAttestationEnricher succeeding = new LedgerAttestationEnricher() {
            @Override
            public void enrich(LedgerAttestation attestation, LedgerEntry entry) {
                attestation.relevanceSnapshot = "{\"ok\":true}";
            }

            @Override
            public int priority() {
                return Integer.MAX_VALUE - 1;
            }

            @Override
            public String name() {
                return "succeeding";
            }
        };

        var pipeline = new AttestationEnricherPipelineCore(List.of(failing, succeeding));
        var att = new LedgerAttestation();
        pipeline.enrich(att, new TestEntry());
        assertEquals("{\"ok\":true}", att.relevanceSnapshot);
    }

    @Test
    void priorityOrdering_lowerPriorityRunsFirst() {
        StringBuilder order = new StringBuilder();
        LedgerAttestationEnricher first = new LedgerAttestationEnricher() {
            @Override
            public void enrich(LedgerAttestation attestation, LedgerEntry entry) {
                order.append("A");
            }

            @Override
            public int priority() {
                return 10;
            }

            @Override
            public String name() {
                return "first";
            }
        };
        LedgerAttestationEnricher second = new LedgerAttestationEnricher() {
            @Override
            public void enrich(LedgerAttestation attestation, LedgerEntry entry) {
                order.append("B");
            }

            @Override
            public int priority() {
                return 20;
            }

            @Override
            public String name() {
                return "second";
            }
        };

        var pipeline = new AttestationEnricherPipelineCore(List.of(second, first));
        pipeline.enrich(new LedgerAttestation(), new TestEntry());
        assertEquals("AB", order.toString());
    }

    @Test
    void hasEnrichers_noEnrichers_returnsFalse() {
        assertFalse(new AttestationEnricherPipelineCore(List.of()).hasEnrichers());
    }

    @Test
    void hasEnrichers_withEnrichers_returnsTrue() {
        LedgerAttestationEnricher enricher = new LedgerAttestationEnricher() {
            @Override
            public void enrich(LedgerAttestation attestation, LedgerEntry entry) {}

            @Override
            public String name() {
                return "stub";
            }
        };
        assertTrue(new AttestationEnricherPipelineCore(List.of(enricher)).hasEnrichers());
    }
}
