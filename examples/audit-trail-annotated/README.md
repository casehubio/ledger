# Example: Audit Trail (Annotated)

The **recommended approach** for recording audit entries with `casehub-ledger`.
Add `@Audited` to any CDI method and the interceptor transparently records
an immutable ledger entry on successful execution.

## What this demonstrates

| Annotation | What it does | Method |
|-----------|-------------|--------|
| `@Audited` | Records an EVENT audit entry | `upload`, `approve`, `reject`, `archive` |
| `@Audited(entryType = COMMAND)` | Records a COMMAND (requested action) | `submitForReview` |
| `@Audited(actorRole = "...")` | Tracks the actor's role | All methods |
| `@Audited(auditFailures = true)` | Also records failed attempts | `reject` |
| `@SubjectId` | Aggregate key (determines Merkle tree) | All methods (required) |
| `@ActorId` | Override actor identity (delegation) | All methods |
| `@TenancyId` | Override tenancy (non-HTTP contexts) | `archive` |

## When to use this vs domain subclasses

| Approach | Use when | Example |
|----------|---------|---------|
| **Annotations** (this example) | You need a simple audit trail. Domain context in `domainData` (JSON) is sufficient. | Most consumers |
| **Domain subclass** (order-processing) | You need typed, SQL-queryable domain columns on the ledger entry. | Consumers with complex audit queries |

## Prerequisites

```bash
cd ../../   # casehub-ledger root
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install -DskipTests
```

## Run tests

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test
```

5 tests cover: basic audit, COMMAND vs EVENT entry types, actor role tracking,
multi-operation audit trail, and non-HTTP context with `@TenancyId`.
