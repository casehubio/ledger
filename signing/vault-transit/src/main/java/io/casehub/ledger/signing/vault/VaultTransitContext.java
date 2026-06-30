package io.casehub.ledger.signing.vault;

import java.security.PublicKey;

/**
 * Per-actorId context cached by the Quarkus adapter.
 * Holds the Vault Transit key name and the public key fetched from Vault.
 * The private key never leaves Vault.
 *
 * @param keyName   Vault Transit key name
 * @param publicKey Ed25519 public key fetched from Vault
 */
public record VaultTransitContext(String keyName, PublicKey publicKey) {}
