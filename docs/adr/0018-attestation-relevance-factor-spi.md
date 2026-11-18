# ADR 0018 — AttestationRelevanceFactor SPI

**Date:** 2026-11-18
**Status:** Accepted
**Refs:** #231

## Context

Trust scoring in `TrustScoreComputer` applies a four-factor weight chain per attestation:
`decay × confidence × credibility × normalised_relevance`. Before #231, three of these
factors existed (decay via `DecayFunction`, confidence as a field, credibility via
`AttestorCredibilityPolicy`) but the fourth — relevance — was absent. All attestations
contributed equally regardless of how relevant the attestor's context was to the scored
actor's work.

Two kinds of attestor-specific weighting serve different purposes:

- **Credibility** is attestor-scoped — "how much do we trust this attestor in general?"
  It reflects the attestor's own track record and applies identically to every attestation
  that attestor makes, regardless of which actor or decision is being assessed.
- **Relevance** is pair-scoped — "how relevant is this specific attestation to this
  specific trust computation?" It reflects the relationship between the attestor and the
  scored actor: organisational proximity, capability overlap, collaboration recency. The
  same attestor may be highly relevant to one actor (same team, overlapping capabilities)
  and irrelevant to another (different org, no shared context).

Conflating relevance into credibility would mean an attestor's weight could never differ
across the actors they attest. Separating them allows independent variation along both
axes.

## Decision

Introduce `AttestationRelevanceFactor` as a CDI SPI (discovered via `Instance<>`). Each
factor computes one dimension of relevance. `RelevanceComposer` aggregates all discovered
factors by multiplying their weights, with a configurable floor
(`casehub.ledger.trust-score.relevance.min-weight`, default 0.5) to prevent compounding
from driving weights to near-zero.

### Composition model

The raw relevance weight per attestation is:

```
rawRelevance = max(minWeight, ∏ factor.assess(ctx).weight())
```

This raw weight feeds into mass-preserving normalisation inside `TrustScoreComputer`:
a first pass sums `baseMass` (decay × confidence × credibility) and
`baseMass × rawRelevance`, then computes `normFactor = sumBase / sumRelevance`. In the
second pass, each attestation's effective relevance is `rawRelevance × normFactor` — which
may exceed 1.0 for above-mean attestations. This preserves the total evidence mass: the
sum of effective weights equals the sum of base weights, so relevance redistributes
influence without inflating or deflating the aggregate score.

### Write-time snapshot

Attestation relevance data is captured at write time by `LedgerAttestationEnricher`
implementations. Each enricher writes its own keys into the attestation's
`relevanceSnapshot` JSON map (e.g. `"org-distance": 2`). The repository clears
caller-supplied values before running the enricher pipeline — the snapshot is
system-derived only.

At trust computation time, `TrustScoreCalculator` builds `AttestationRelevanceContext`
records from the persisted snapshot, so factors read stale-free data without I/O. This
means relevance reflects conditions at attestation time, not recomputation time — an
intentional auditability choice: the weight an attestation received is reproducible from
its persisted snapshot.

### Extensibility via JSON snapshot

The snapshot map is the extensibility mechanism. Adding a new relevance dimension requires:

1. A new `LedgerAttestationEnricher` bean that writes the dimension's key
2. A new `AttestationRelevanceFactor` bean that reads it

No schema migration, no record change, no breaking change to `AttestationRelevanceContext`.
Typed columns were rejected because each new dimension would require a schema migration and
a nullable column on the attestation table — overhead disproportionate to the value, given
that the snapshot is consumed programmatically by factors, not queried directly.

### Accuracy vs independence

Shared operational context between attestor and scored actor creates a tension:

- **Accuracy bias:** an attestor who works alongside an actor has better information about
  that actor's work quality. Weighting them higher improves accuracy.
- **Independence bias:** correlated attestors provide redundant evidence. Weighting them
  lower improves statistical independence.

The default is accuracy-biased: an org-proximity factor (contributed by consumers, not by
ledger) would increase weight for closer attestors. Independence-biased factors can be
contributed through the same SPI — for instance, a factor that penalises attestors who
share the same team. The composition model (product with floor) allows both biases to
coexist: accuracy factors and independence factors multiply together, and the floor
prevents extreme outcomes.

### Self-attestation exclusion

`TrustScoreComputer` excludes self-attestations (where `attestorId == entry.actorId`)
before any weight is applied. This is a structural exclusion in the computation loop,
not a relevance factor — an actor attesting their own work is semantically invalid
regardless of relevance.

## Consequences

- Zero factors on the classpath → `RelevanceComposer` returns 1.0 for all attestations.
  The weight chain degrades gracefully to `decay × confidence × credibility`, preserving
  backward compatibility.
- `AttestationWeights` groups `credibilityByAttestorId` and `relevanceByAttestationId` as a
  single parameter object, preventing positional parameter errors at the
  `TrustScoreComputer.compute()` call site.
- `TrustScoreCalculator` batch-resolves relevance via `RelevanceComposer` once per
  per-actor computation pass. Factors that need I/O should use the `assessBatch` default
  method to amortise queries.
- The `relevanceSnapshot` JSON on `LedgerAttestation` is append-only in practice: once
  written, the snapshot is immutable (the enricher pipeline only runs at write time).
  This supports compliance audit trails — the basis for a weight is permanently recorded.
- Future work: concrete `AttestationRelevanceFactor` implementations (org-proximity,
  capability-overlap) live in consumers (casehub-work, casehub-qhorus), not in ledger.
  Ledger provides the SPI and composition; domain knowledge stays in the domain.

## References

- ADR 0007 — DecayFunction SPI (precedent: extracting a trust weight as an SPI)
- ADR 0008 — GlobalScoreStrategy SPI (precedent: strategy pattern for trust computation)
- ADR 0009 — Continuous scores: decay-weighted average (precedent: weight-chain design)
- Issue #231 — Implementation: org-scoped attestation weighting
