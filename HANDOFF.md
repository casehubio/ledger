# HANDOFF.md — casehub-ledger

**Session:** 2026-06-26
**Branch:** main (closed issue-158-global-pass-raw-attestations)

## What happened

Fixed #158 — global pass in `TrustScoreCalculator` still masked FLAGGED attestations via WEIGHTED_MAJORITY aggregation. Used raw attestations in the global pass (same as capability-pass fix c45f126). Removed dead aggregation code. Net -67 lines. TDD.

Consumer (devtown) reported the fix didn't take effect — traced to Quarkus augmentation cache. Consumer needs `mvn clean test` after picking up the updated SNAPSHOT; without `clean`, Quarkus reuses stale augmented bytecode referencing the old 4-arg constructor.

**Paused:** #137 (artifact trust scoring) is on the pause stack.

## What's Next

| # | Description | Scale | Complexity | Blocked by | Blocks | Notes |
|---|-------------|-------|------------|------------|--------|-------|
| #137 | Artifact trust scoring — extend Bayesian Beta model to content-hashed artifacts | — | — | — | — | Paused; first consumer: casehub-ops LlmProvisioner |
| #102 | Cloud KMS AgentSigner adapters (AWS, GCP, Azure) | L | Med | — | — | |
| #101 | Vault AppRole/OIDC auth for VaultTransitAgentSigner | M | High | — | — | Dynamic auth only; static token shipped in #85 |
| #96 | Code-generation for reactive service tier (Vert.x codegen approach) | L | High | — | — | Not worth pursuing until pair count grows past 5+ |

## Cross-Module

No active cross-repo blockers.
