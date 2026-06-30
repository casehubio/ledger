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
import io.casehub.ledger.signing.gcp.GcpKmsSigningConfig;
import io.quarkus.scheduler.Scheduled;

/**
 * {@link io.casehub.ledger.runtime.service.AgentSigner} that delegates signing to GCP Cloud KMS.
 *
 * <p>The private key never leaves GCP Cloud KMS. Only the public key is fetched and cached locally
 * (for storage on {@code LedgerEntry.agentPublicKey}, needed by {@code AgentCryptographicVerifier}).
 *
 * <p><strong>Auth:</strong> This adapter uses Application Default Credentials
 * ({@code GOOGLE_APPLICATION_CREDENTIALS} or GCE metadata).
 *
 * <p><strong>Algorithm support:</strong> Only EC key algorithms are supported (P-256, P-384).
 * RSA and secp256k1 key algorithms are rejected at {@code loadContext()} time (the pure Java
 * client logs an error and returns {@code null}, which this adapter caches as {@code Optional.empty()}).
 *
 * <p><strong>Digest selection:</strong> The adapter reads {@code CryptoKeyVersion.algorithm}
 * and selects the matching digest (P-256 → SHA-256, P-384 → SHA-384).
 *
 * <p><strong>Activation:</strong>
 * <pre>
 * quarkus.arc.selected-alternatives=io.casehub.ledger.signing.gcp.quarkus.GcpKmsAgentSigner
 * </pre>
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
        this.client = new GcpKmsSigningClient(
                new GcpKmsSigningConfig(config.keyMapping()));
    }

    // Visible for testing — allows injecting a pre-configured client
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
        final PublicKey publicKey = client.fetchPublicKey(versionName);
        if (publicKey == null) {
            // RSA key or not found — client already logged the reason
            return Optional.empty();
        }
        // Fetch algorithm for sign-time digest selection
        final com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm algorithm =
                fetchAlgorithm(versionName);
        return Optional.of(new GcpKmsContext(versionName, publicKey, algorithm));
    }

    @Override
    protected AgentSignature performSign(final String actorId, final GcpKmsContext context,
            final byte[] data) {
        final byte[] sigBytes = client.sign(context.versionName(), data);
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

    /**
     * Invalidates the cached context for the rotated actor.
     * Required by {@link AbstractCachingAgentSigner} contract — concrete CDI subclasses
     * must expose {@code onKeyRotated()} as a CDI observer.
     */
    public void onKeyRotated(@Observes final AgentKeyRotatedEvent event) {
        super.onKeyRotated(event);
    }

    private com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm fetchAlgorithm(
            final String versionName) {
        // Fetch via the client's embedded wrapper (works in both production and test mode)
        return client.getWrapper().getCryptoKeyVersion(versionName).getAlgorithm();
    }
}
