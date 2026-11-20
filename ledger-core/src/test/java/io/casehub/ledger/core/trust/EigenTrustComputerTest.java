package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EigenTrustComputerTest {

    private final EigenTrustComputer computer = new EigenTrustComputer(0.5);

    private LedgerAttestation attestation(UUID id, String attestorId,
            UUID entryId, AttestationVerdict verdict) {
        var att = new LedgerAttestation();
        att.id = id;
        att.attestorId = attestorId;
        att.ledgerEntryId = entryId;
        att.verdict = verdict;
        att.confidence = 1.0;
        att.occurredAt = Instant.now();
        return att;
    }

    @Test
    void uniformWeightsMatchUnweighted() {
        UUID e1 = UUID.randomUUID(), e2 = UUID.randomUUID();
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID();
        var att1 = attestation(a1, "alice", e2, AttestationVerdict.SOUND);
        var att2 = attestation(a2, "bob", e1, AttestationVerdict.SOUND);

        Map<UUID, String> entryActorIndex = Map.of(e1, "alice", e2, "bob");
        var atts = List.of(att1, att2);

        Map<String, Double> unweighted = computer.compute(atts, entryActorIndex, Set.of());
        Map<String, Double> weighted = computer.compute(atts, entryActorIndex, Set.of(),
                Map.of(a1, 1.0, a2, 1.0));

        assertEquals(unweighted.get("alice"), weighted.get("alice"), 1e-9);
        assertEquals(unweighted.get("bob"), weighted.get("bob"), 1e-9);
    }

    @Test
    void highWeightAttestationDominates() {
        UUID eAlice = UUID.randomUUID();
        UUID ac = UUID.randomUUID(), ad = UUID.randomUUID();
        var attCharlie = attestation(ac, "charlie", eAlice, AttestationVerdict.SOUND);
        var attDave = attestation(ad, "dave", eAlice, AttestationVerdict.FLAGGED);

        UUID eCharlie = UUID.randomUUID(), eDave = UUID.randomUUID();
        Map<UUID, String> entryActorIndex = Map.of(
                eAlice, "alice", eCharlie, "charlie", eDave, "dave");

        var atts = List.of(attCharlie, attDave);
        Map<UUID, Double> weights = Map.of(ac, 1.0, ad, 0.1);

        Map<String, Double> scores = computer.compute(atts, entryActorIndex, Set.of(), weights);

        assertTrue(scores.getOrDefault("alice", 0.0) > 0.0,
                "alice should have positive trust when high-weight SOUND dominates low-weight FLAGGED");
    }

    @Test
    void emptyWeightsMapDefaultsToUnweighted() {
        UUID e1 = UUID.randomUUID(), a1 = UUID.randomUUID();
        var att = attestation(a1, "alice", e1, AttestationVerdict.SOUND);

        Map<UUID, String> entryActorIndex = Map.of(e1, "bob");
        var atts = List.of(att);

        Map<String, Double> withEmpty = computer.compute(atts, entryActorIndex, Set.of(), Map.of());
        Map<String, Double> without = computer.compute(atts, entryActorIndex, Set.of());

        assertEquals(without.get("alice"), withEmpty.get("alice"), 1e-9);
    }
}
