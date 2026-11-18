package io.casehub.ledger.api.spi;

import io.casehub.platform.api.identity.ActorType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Per-attestation context for relevance assessment. Carries the attestation's
 * identity, capability, timing, and write-time snapshot — everything a
 * relevance factor needs to compute a weight without I/O.
 *
 * <p>The {@code snapshot} map is the extensibility mechanism: each enricher
 * writes its own keys (e.g. {@code "org-distance": 2}), and each factor reads
 * its own key. Adding a new factor requires only a new enricher and factor
 * bean — no record or schema changes in ledger.
 *
 * <p>Adding a component to this record is a source-breaking change for callers
 * (canonical constructor changes). The snapshot map avoids this for
 * enricher-provided data.
 */
public record AttestationRelevanceContext(
        UUID attestationId,
        String attestorId,
        ActorType attestorType,
        String attestorRole,
        String scoredActorId,
        String tenancyId,
        String capabilityTag,
        String trustDimension,
        Instant occurredAt,
        Map<String, Object> snapshot
) {}
