CREATE TABLE trust_score_snapshot (
    id              UUID            NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    capability_tag  VARCHAR(255),
    score           DOUBLE PRECISION NOT NULL,
    previous_score  DOUBLE PRECISION NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_trust_score_snapshot PRIMARY KEY (id)
);

CREATE INDEX idx_trust_score_snapshot_actor_cap
    ON trust_score_snapshot (actor_id, capability_tag, occurred_at DESC);
