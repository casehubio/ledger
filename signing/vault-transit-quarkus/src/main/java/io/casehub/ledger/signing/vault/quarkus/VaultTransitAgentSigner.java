package io.casehub.ledger.signing.vault.quarkus;

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
import io.casehub.ledger.signing.vault.VaultTransitContext;
import io.casehub.ledger.signing.vault.VaultTransitSigningClient;
import io.casehub.ledger.signing.vault.VaultTransitSigningConfig;
import io.quarkus.scheduler.Scheduled;

/**
 * {@link io.casehub.ledger.runtime.service.AgentSigner} that delegates signing to
 * HashiCorp Vault Transit Secrets Engine.
 *
 * <p>The private key never leaves Vault. Only the public key is fetched and cached locally
 * (for storage on {@code LedgerEntry.agentPublicKey}, needed by {@code AgentCryptographicVerifier}).
 *
 * <p><strong>Auth:</strong> This adapter uses a static Vault token
 * ({@code casehub.ledger.vault-transit.token}). Production deployments should use
 * AppRole or OIDC. See issue #101.
 *
 * <p><strong>Algorithm support:</strong> Only {@code ed25519} Vault Transit key types are
 * supported. The pure Java {@link VaultTransitSigningClient} validates key types and
 * handles PEM parsing.
 *
 * <p><strong>Activation:</strong>
 * <pre>
 * quarkus.arc.selected-alternatives=io.casehub.ledger.signing.vault.quarkus.VaultTransitAgentSigner
 * </pre>
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class VaultTransitAgentSigner extends AbstractCachingAgentSigner<VaultTransitContext> {

    private static final Logger LOG = Logger.getLogger(VaultTransitAgentSigner.class);

    private final VaultTransitSigningClient client;
    private final VaultTransitConfig config;

    @Inject
    public VaultTransitAgentSigner(final VaultTransitConfig config) {
        this.config = config;
        this.client = new VaultTransitSigningClient(
                new VaultTransitSigningConfig(config.address(), config.token(), config.keyMapping()));
    }

    // Visible for testing — allows injecting a pre-configured client
    VaultTransitAgentSigner(final VaultTransitConfig config, final VaultTransitSigningClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    protected Optional<VaultTransitContext> loadContext(final String actorId) {
        final String keyName = config.keyMapping().get(actorId);
        if (keyName == null) {
            LOG.debugf("No Vault Transit key configured for actor %s — skipping signing", actorId);
            return Optional.empty();
        }
        final PublicKey publicKey = client.fetchPublicKey(keyName);
        return Optional.of(new VaultTransitContext(keyName, publicKey));
    }

    @Override
    protected AgentSignature performSign(final String actorId, final VaultTransitContext context,
            final byte[] data) {
        final byte[] sigBytes = client.sign(context.keyName(), data);
        final byte[] pubEncoded = context.publicKey().getEncoded();
        final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
        return new AgentSignature(sigBytes, pubEncoded, keyRef);
    }

    @Override
    protected PublicKey contextPublicKey(final VaultTransitContext context) {
        return context.publicKey();
    }

    @Scheduled(every = "${casehub.ledger.vault-transit.refresh-interval:5m}")
    void refreshCache() {
        LOG.debug("Invalidating Vault Transit context cache");
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
