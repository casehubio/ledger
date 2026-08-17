package io.casehub.ledger.api.model;

import java.time.Instant;
import java.util.UUID;

import io.casehub.platform.api.identity.ActorType;


/**
 * A peer attestation stamped onto a {@link LedgerEntry}.
 *
 * <p>Carries either a binary verdict ({@code verdict}) for trust scoring, or a continuous
 * quality score ({@code dimensionScore} ∈ [0.0, 1.0]) for dimension-labelled scoring when
 * {@code trustDimension} is set. Both fields may be populated together.
 */
public class LedgerAttestation {

    public UUID id;

    public UUID ledgerEntryId;

    public UUID subjectId;

    public String attestorId;

    public ActorType attestorType;

    public String attestorRole;

    public AttestationVerdict verdict;

    public String evidence;

    public double confidence;

    public String capabilityTag = CapabilityTag.GLOBAL;

    /** Application-defined quality dimension label (e.g. {@code "review-thoroughness"}). Null on ordinary attestations. */
    public String trustDimension;

    /**
     * Continuous quality score in [0.0, 1.0]. Only meaningful when {@code trustDimension} is
     * set. Null on ordinary (binary verdict) attestations.
     */
    public Double dimensionScore;

    public Instant occurredAt;
}
