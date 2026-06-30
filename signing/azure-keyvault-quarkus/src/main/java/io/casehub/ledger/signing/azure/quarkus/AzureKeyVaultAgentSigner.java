package io.casehub.ledger.signing.azure.quarkus;

import java.security.PublicKey;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.ledger.runtime.service.AbstractCachingAgentSigner;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.signing.azure.AzureKeyVaultClientWrapper;
import io.casehub.ledger.signing.azure.AzureKeyVaultContext;
import io.casehub.ledger.signing.azure.AzureKeyVaultSigningClient;
import io.casehub.ledger.signing.azure.AzureKeyVaultSigningConfig;
import io.quarkus.scheduler.Scheduled;

/**
 * {@link io.casehub.ledger.runtime.service.AgentSigner} that delegates signing to Azure Key Vault.
 *
 * <p>The private key never leaves Azure Key Vault. Only the public key is fetched and cached locally
 * (for storage on {@code LedgerEntry.agentPublicKey}, needed by {@code AgentCryptographicVerifier}).
 *
 * <p><strong>Auth:</strong> This adapter uses {@code DefaultAzureCredential} (env vars, managed identity, Azure CLI).
 *
 * <p><strong>Algorithm support:</strong> Only EC key types are supported (P-256, P-384, P-521).
 * RSA key types are rejected at {@code loadContext()} time (the pure Java client logs an error
 * and returns {@code Optional.empty()}).
 *
 * <p><strong>Activation:</strong>
 * <pre>
 * quarkus.arc.selected-alternatives=io.casehub.ledger.signing.azure.quarkus.AzureKeyVaultAgentSigner
 * </pre>
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class AzureKeyVaultAgentSigner extends AbstractCachingAgentSigner<AzureKeyVaultContext> {

    private static final Logger LOG = Logger.getLogger(AzureKeyVaultAgentSigner.class);

    private final AzureKeyVaultSigningClient client;
    private final AzureKeyVaultConfig config;

    @Inject
    public AzureKeyVaultAgentSigner(final AzureKeyVaultConfig config) {
        this.config = config;
        this.client = new AzureKeyVaultSigningClient(
                new AzureKeyVaultSigningConfig(config.keyMapping()));
    }

    // Visible for testing — allows injecting a mocked wrapper
    AzureKeyVaultAgentSigner(final AzureKeyVaultConfig config,
            final AzureKeyVaultClientWrapper wrapper) {
        this.config = config;
        this.client = new AzureKeyVaultSigningClient(
                new AzureKeyVaultSigningConfig(config.keyMapping()), wrapper);
    }

    @Override
    protected Optional<AzureKeyVaultContext> loadContext(final String actorId) {
        final String keyRef = config.keyMapping().get(actorId);
        if (keyRef == null) {
            LOG.debugf("No Azure Key Vault key configured for actor %s — skipping signing", actorId);
            return Optional.empty();
        }
        return client.fetchPublicKey(keyRef);
    }

    @Override
    protected AgentSignature performSign(final String actorId, final AzureKeyVaultContext context,
            final byte[] data) {
        // Build key reference from context (format: "vaultUrl#keyName")
        final String keyRef = context.vaultUrl() + "#" + context.keyName();
        final byte[] sigBytes = client.sign(keyRef, data);
        final byte[] pubEncoded = context.publicKey().getEncoded();
        final String keyRefStr = AgentSignature.computeKeyRef(pubEncoded);
        return new AgentSignature(sigBytes, pubEncoded, keyRefStr);
    }

    @Override
    protected PublicKey contextPublicKey(final AzureKeyVaultContext context) {
        return context.publicKey();
    }

    @Scheduled(every = "${casehub.ledger.azure-keyvault.refresh-interval:24h}")
    void refreshCache() {
        LOG.debug("Invalidating Azure Key Vault context cache");
        invalidateAll();
    }

    /**
     * Invalidates the cached context for the rotated actor.
     * Required by {@link AbstractCachingAgentSigner} contract — concrete CDI subclasses
     * must expose {@code onKeyRotated()} as a CDI observer.
     */
    public void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}
