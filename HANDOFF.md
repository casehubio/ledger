# HANDOFF.md — casehub-ledger

**Session:** 2026-06-25
**Branch:** main

## What happened

Handover refresh — previous handover (2026-06-12) was stale. Issues #100 and #123 from the old "What's Next" list are now closed. Updated open issue inventory.

## What's Next

| # | Description | Scale | Complexity | Blocked by | Blocks | Notes |
|---|-------------|-------|------------|------------|--------|-------|
| #137 | Artifact trust scoring — extend Bayesian Beta model to content-hashed artifacts | — | — | — | — | First consumer: casehub-ops LlmProvisioner |
| #102 | Cloud KMS AgentSigner adapters (AWS, GCP, Azure) | L | Med | — | — | |
| #101 | Vault AppRole/OIDC auth for VaultTransitAgentSigner | M | High | — | — | Dynamic auth only; static token shipped in #85 |
| #96 | Code-generation for reactive service tier (Vert.x codegen approach) | L | High | — | — | Not worth pursuing until pair count grows past 5+ |

## Cross-Module

No active cross-repo blockers.
