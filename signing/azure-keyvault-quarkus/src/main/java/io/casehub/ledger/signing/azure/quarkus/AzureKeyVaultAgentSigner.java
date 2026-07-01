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
import io.quarkus.scheduler.Scheduled;

/**
 * Activation: {@code quarkus.arc.selected-alternatives=io.casehub.ledger.signing.azure.quarkus.AzureKeyVaultAgentSigner}
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
        this.client = new AzureKeyVaultSigningClient();
    }

    AzureKeyVaultAgentSigner(final AzureKeyVaultConfig config,
            final AzureKeyVaultClientWrapper wrapper) {
        this.config = config;
        this.client = new AzureKeyVaultSigningClient(wrapper);
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
        final byte[] sigBytes = client.sign(
                context.vaultUrl(), context.keyName(), context.algorithm(), data);
        final byte[] pubEncoded = context.publicKey().getEncoded();
        final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
        return new AgentSignature(sigBytes, pubEncoded, keyRef);
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

    public void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}
