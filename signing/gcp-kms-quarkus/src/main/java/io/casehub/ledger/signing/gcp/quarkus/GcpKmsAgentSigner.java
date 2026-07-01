package io.casehub.ledger.signing.gcp.quarkus;

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
import io.casehub.ledger.signing.gcp.GcpKmsClientWrapper;
import io.casehub.ledger.signing.gcp.GcpKmsContext;
import io.casehub.ledger.signing.gcp.GcpKmsSigningClient;
import io.quarkus.scheduler.Scheduled;

/**
 * Activation: {@code quarkus.arc.selected-alternatives=io.casehub.ledger.signing.gcp.quarkus.GcpKmsAgentSigner}
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class GcpKmsAgentSigner extends AbstractCachingAgentSigner<GcpKmsContext> {

    private static final Logger LOG = Logger.getLogger(GcpKmsAgentSigner.class);

    private final GcpKmsSigningClient client;
    private final GcpKmsConfig config;

    @Inject
    public GcpKmsAgentSigner(final GcpKmsConfig config) {
        this.config = config;
        this.client = new GcpKmsSigningClient();
    }

    GcpKmsAgentSigner(final GcpKmsConfig config, final GcpKmsSigningClient client) {
        this.config = config;
        this.client = client;
    }

    @Override
    protected Optional<GcpKmsContext> loadContext(final String actorId) {
        final String versionName = config.keyMapping().get(actorId);
        if (versionName == null) {
            LOG.debugf("No GCP Cloud KMS key configured for actor %s — skipping signing", actorId);
            return Optional.empty();
        }
        return Optional.ofNullable(client.fetchPublicKey(versionName));
    }

    @Override
    protected AgentSignature performSign(final String actorId, final GcpKmsContext context,
            final byte[] data) {
        final byte[] sigBytes = client.sign(context.versionName(), context.algorithm(), data);
        final byte[] pubEncoded = context.publicKey().getEncoded();
        final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
        return new AgentSignature(sigBytes, pubEncoded, keyRef);
    }

    @Override
    protected PublicKey contextPublicKey(final GcpKmsContext context) {
        return context.publicKey();
    }

    @Scheduled(every = "${casehub.ledger.gcp-kms.refresh-interval:5m}")
    void refreshCache() {
        LOG.debug("Invalidating GCP Cloud KMS context cache");
        invalidateAll();
    }

    public void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }
}
