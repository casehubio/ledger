# HANDOFF.md — casehub-ledger

**Session:** 2026-06-25
**Branch:** main (closed issue-158-global-pass-raw-attestations)

## What happened

Fixed #158 — global pass in `TrustScoreCalculator` was still masking FLAGGED attestations via WEIGHTED_MAJORITY aggregation. The c45f126 capability-pass fix used raw attestations, but the global pass still fed through `buildEffectiveAttestations()`. FLAGGED attestations on entries with SOUND attestations never incremented beta in the global score.

Fix: use `rawAttestations` in the global pass too. Removed dead aggregation code (`buildEffectiveAttestations`, `toSynthetic`, unused fields). Net -67 lines. TDD: test asserts `attestationNegative == 1` when FLAGGED exists.

Also refreshed the handover — #100 and #123 were stale (both closed).

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
