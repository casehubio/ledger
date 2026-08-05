# io.casehub.ledger.api.spi.ActorIdentityProvider

**Package:** `io.casehub.ledger.api.spi`

**Kind:** `interface`

SPI for pseudonymising actor identities written to the ledger.

<p>
The default implementation is pass-through — existing consumers see zero behaviour change.
Replace with a custom CDI bean to plug in any pseudonymisation strategy.
The built-in `InternalActorIdentityProvider` activates when
`casehub.ledger.identity.tokenisation.enabled=true`.

## Methods

### `public abstract void erase(java.lang.String rawActorId)`

Severs the token→identity mapping for `rawActorId`.
After this call, `.resolve(String)` for the actor's token returns empty.
Ledger entries retaining the token become permanently anonymous.

#### Parameters

- `rawActorId` (`java.lang.String`) — the real actor identity whose mapping to sever; `null` is treated as a no-op

### `public abstract java.util.Optional<java.lang.String> resolve(java.lang.String token)`

Maps a stored token back to the real identity.
Returns `Optional.empty()` if the mapping has been severed by erasure
or never existed.

#### Parameters

- `token` (`java.lang.String`) — the stored token

#### Returns

the real identity, or empty if unresolvable

### `public abstract java.lang.String tokenise(java.lang.String rawActorId, ActorType actorType)`

Returns a token to store in place of `rawActorId` on write.
Creates a new mapping if one does not yet exist.
Called on every `save()` and `saveAttestation()`.

<p>Only `ActorType.HUMAN` actors (and null actorType as a safe default)
are tokenised. Non-human actors (SYSTEM, AGENT) are returned unchanged —
they are not natural persons and have no GDPR pseudonymisation obligation.

<p>
Implementations should avoid expensive blocking operations (e.g. JPA queries) in
hot persistence paths. The built-in implementations are non-blocking.

#### Parameters

- `rawActorId` (`java.lang.String`) — the real actor identity; may be `null`
- `actorType` (`ActorType`) — the type of actor; `null` is treated as potentially human (tokenised)

#### Returns

token to store, or `null` if input is `null`

### `public abstract java.util.Optional<java.lang.String> tokeniseForQuery(java.lang.String rawActorId)`

Returns the stored query key for `rawActorId` without creating a token mapping.

<p>When a token mapping exists, returns `Optional.of(token)`. When no mapping exists
(SYSTEM/AGENT actors are never tokenised and are stored under their raw identity), returns
`Optional.of(rawActorId)`. Returns `Optional.empty()` only when input is
`null`.

<p>Callers can distinguish "token found" from "no mapping" by comparing the returned
value to the raw input: equal → no mapping, use raw identity for the query; not equal →
token found, use token for the query. Both cases produce a non-empty Optional and should
proceed with the query. The empty case (null input) is the only short-circuit.

<p>Called on read queries (`findByActorId`) to avoid spurious token creation.

#### Parameters

- `rawActorId` (`java.lang.String`) — the real actor identity; `null` returns empty

#### Returns

query key to use (token or raw actorId), or empty for null input
