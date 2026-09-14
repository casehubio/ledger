---
id: PP-20260914-7387db
title: "@McpDomain values use / for hierarchical MCP discovery — never flatten"
type: rule
scope: platform
applies_to: "any @McpDomain SPI interface across all casehub repos"
severity: important
refs:
  - graphql-generator/src/main/java/io/casehub/platform/graphql/generator/GraphQLResolverProcessor.java
violation_hint: "flat domain like 'ledger-entries' instead of 'ledger/entries'; generated class name containing / instead of PascalCase"
created: 2026-09-14
---

`@McpDomain` values must use `/` separators for hierarchical MCP progressive discovery
(e.g. `"ledger/entries"`, not `"ledger-entries"`). The hierarchy lets MCP clients discover
`ledger` as a top-level domain, then drill into `entries`, `attestations`, `verification`,
`trust` as sub-domains. The `graphql-generator` APT must handle `/` in domain names by
converting to PascalCase for class names (`ledger/entries` → `GeneratedLedgerEntriesResource`)
and kebab-case for REST paths (`/api/ledger-entries/...`). If the generator produces invalid
class names (containing `/`), fix the generator — do not flatten the domain hierarchy.
