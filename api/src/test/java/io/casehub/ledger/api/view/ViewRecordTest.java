package io.casehub.ledger.api.view;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ViewRecordTest {

    @Test
    void ledgerEntryViewHasAllFields() {
        var view = new LedgerEntryView(
                UUID.randomUUID(), UUID.randomUUID(), "tenant-1", 1,
                "EVENT", "actor-1", "AGENT", "reviewer",
                Instant.now(), "abc123", "trace-1",
                UUID.randomUUID(), "{}", Map.of("key", "value"));
        assertNotNull(view.id());
        assertEquals("EVENT", view.entryType());
        assertEquals("reviewer", view.actorRole());
        assertEquals("abc123", view.digest());
    }

    @Test
    void ledgerEntryPageHasCorrectStructure() {
        var entries = List.of(new LedgerEntryView(
                UUID.randomUUID(), UUID.randomUUID(), "t", 0,
                "EVENT", "a", "AGENT", null,
                Instant.now(), null, null, null, null, null));
        var page = new LedgerEntryPage(entries, 1, false);
        assertEquals(1, page.totalCount());
        assertFalse(page.hasMore());
        assertEquals(1, page.entries().size());
    }

    @Test
    void attestationViewHasAllFields() {
        var view = new AttestationView(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "attestor-1", "AGENT", "reviewer",
                "SOUND", "evidence", 0.95, "capability-1",
                "accuracy", 0.9, Instant.now());
        assertEquals("SOUND", view.verdict());
        assertEquals(0.95, view.confidence());
    }

    @Test
    void verificationViewHasCorrectStructure() {
        var view = new VerificationView(UUID.randomUUID(), "rootHash", true);
        assertTrue(view.verified());
        assertEquals("rootHash", view.treeRoot());
    }

    @Test
    void inclusionProofViewHasNestedSteps() {
        var steps = List.of(new InclusionProofView.ProofStepView("hash1", "LEFT"));
        var view = new InclusionProofView(UUID.randomUUID(), 0, 3, "leaf", steps, "root");
        assertEquals(1, view.siblings().size());
        assertEquals("LEFT", view.siblings().get(0).side());
    }

    @Test
    void trustScoreViewHandlesEmptyOptional() {
        var view = new TrustScoreView("actor-1", OptionalDouble.empty(), Map.of(), Map.of());
        assertTrue(view.globalScore().isEmpty());
    }

    @Test
    void capabilityScoreViewHasAllFields() {
        var view = new CapabilityScoreView("actor-1", "cap-1",
                OptionalDouble.of(0.85), 10, Map.of("accuracy", 0.9));
        assertEquals(10, view.decisionCount());
    }

    @Test
    void trustRoutingProfileViewHasAllFields() {
        var view = new TrustRoutingProfileView("actor-1", "cap-1",
                OptionalDouble.of(0.8), OptionalDouble.of(0.9),
                5, Map.of("accuracy", 0.85));
        assertEquals(5, view.decisionCount());
    }

    @Test
    void appendEntryRequestDoesNotContainTenancyId() {
        var components = AppendEntryRequest.class.getRecordComponents();
        for (var c : components) {
            assertNotEquals("tenancyId", c.getName(),
                    "tenancyId must be a method parameter, not in the request record");
        }
    }

    @Test
    void createAttestationRequestDoesNotContainTenancyId() {
        var components = CreateAttestationRequest.class.getRecordComponents();
        for (var c : components) {
            assertNotEquals("tenancyId", c.getName(),
                    "tenancyId must be a method parameter, not in the request record");
        }
    }
}
