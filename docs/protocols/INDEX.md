# casehub-ledger — Protocol Index

Navigation hub. Each section links to the sub-index for full listings.

## Platform rules

Rules for the casehub-ledger extension architecture.

| File | Rule Summary | Applies To |
|------|-------------|------------|
| [casehub/per-subject-table-tenancy.md](casehub/per-subject-table-tenancy.md) | Per-subject storage tables must include tenancy_id in their key | Any table keyed by subjectId |
| [casehub/ledger-subclass-repo-readonly.md](casehub/ledger-subclass-repo-readonly.md) | LedgerEntry subclass repositories must be read-only — no save() | Any subclass-specific repository |
| [casehub/mcp-domain-hierarchical-naming.md](casehub/mcp-domain-hierarchical-naming.md) | @McpDomain values use `/` for hierarchical MCP discovery — never flatten | Any @McpDomain SPI interface |

→ Full listing: [casehub/INDEX.md](casehub/INDEX.md)
