package io.casehub.ledger.api.spi;

import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttestationRelevanceContextTest {

    @Test
    void fullConstruction() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> snapshot = Map.of("org-distance", 2);

        var ctx = new AttestationRelevanceContext(
                id, "attestor-1", ActorType.AGENT, "reviewer",
                "actor-1", "tenant-1", "code-review", "accuracy",
                now, snapshot);

        assertEquals(id, ctx.attestationId());
        assertEquals("attestor-1", ctx.attestorId());
        assertEquals(ActorType.AGENT, ctx.attestorType());
        assertEquals("reviewer", ctx.attestorRole());
        assertEquals("actor-1", ctx.scoredActorId());
        assertEquals("tenant-1", ctx.tenancyId());
        assertEquals("code-review", ctx.capabilityTag());
        assertEquals("accuracy", ctx.trustDimension());
        assertEquals(now, ctx.occurredAt());
        assertEquals(2, snapshot.get("org-distance"));
    }

    @Test
    void nullSnapshot_accepted() {
        var ctx = new AttestationRelevanceContext(
                UUID.randomUUID(), "a", ActorType.HUMAN, null,
                "b", "t", null, null, null, null);

        assertNull(ctx.snapshot());
    }

    @Test
    void nullOptionalFields_accepted() {
        var ctx = new AttestationRelevanceContext(
                UUID.randomUUID(), "a", null, null,
                "b", "t", null, null, null, null);

        assertNull(ctx.attestorType());
        assertNull(ctx.attestorRole());
        assertNull(ctx.capabilityTag());
        assertNull(ctx.trustDimension());
        assertNull(ctx.occurredAt());
    }

    @Test
    void emptySnapshot_accepted() {
        var ctx = new AttestationRelevanceContext(
                UUID.randomUUID(), "a", ActorType.SYSTEM, null,
                "b", "t", null, null, null, Map.of());

        assertNotNull(ctx.snapshot());
        assertTrue(ctx.snapshot().isEmpty());
    }
}
