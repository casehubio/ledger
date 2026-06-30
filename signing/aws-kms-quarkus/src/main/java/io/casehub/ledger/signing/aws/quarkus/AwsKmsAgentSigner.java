package io.casehub.ledger.signing.aws.quarkus;

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
import io.casehub.ledger.signing.aws.AwsKmsContext;
import io.casehub.ledger.signing.aws.AwsKmsSigningClient;
import io.casehub.ledger.signing.aws.AwsKmsSigningConfig;
import io.quarkus.scheduler.Scheduled;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * {@link io.casehub.ledger.runtime.service.AgentSigner} that delegates signing to AWS KMS.
 *
 * <p>The private key never leaves AWS KMS. Only the public key is fetched and cached locally
 * (for storage on {@code LedgerEntry.agentPublicKey}, needed by {@code AgentCryptographicVerifier}).
 *
 * <p><strong>Auth:</strong> This adapter uses the AWS default credential provider chain
 * (env vars, {@code ~/.aws/credentials}, instance metadata, ECS task role).
 *
 * <p><strong>Algorithm support:</strong> Only EC key specs are supported (P-256, P-384, P-521).
 * RSA key specs are rejected at {@code loadContext()} time (the pure Java client logs an error
 * and returns {@code null}, which this adapter caches as {@code Optional.empty()}).
 *
 * <p><strong>Activation:</strong>
 * <pre>
 * quarkus.arc.selected-alternatives=io.casehub.ledger.signing.aws.quarkus.AwsKmsAgentSigner
 * </pre>
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class AwsKmsAgentSigner extends AbstractCachingAgentSigner<AwsKmsContext> {

    private static final Logger LOG = Logger.getLogger(AwsKmsAgentSigner.class);

    private final AwsKmsSigningClient client;
    private final AwsKmsConfig config;

    @Inject
    public AwsKmsAgentSigner(final AwsKmsConfig config) {
        this.config = config;
        this.client = new AwsKmsSigningClient(
                new AwsKmsSigningConfig(config.region(), config.keyMapping()));
    }

    // Visible for testing — allows injecting a pre-configured client
    AwsKmsAgentSigner(final AwsKmsConfig config, final AwsKmsSigningClient client) {
        this.config = config;
        this.client = client;
    }

    // Visible for testing — allows injecting a mocked KmsClient
    AwsKmsAgentSigner(final AwsKmsConfig config, final KmsClient kmsClient) {
        this.config = config;
        this.client = new AwsKmsSigningClient(
                new AwsKmsSigningConfig(config.region(), config.keyMapping()), kmsClient);
    }

    @Override
    protected Optional<AwsKmsContext> loadContext(final String actorId) {
        final String keyArn = config.keyMapping().get(actorId);
        if (keyArn == null) {
            LOG.debugf("No AWS KMS key configured for actor %s — skipping signing", actorId);
            return Optional.empty();
        }
        final var keyInfo = client.fetchPublicKey(keyArn);
        if (keyInfo == null) {
            // RSA key or not found — client already logged the reason
            return Optional.empty();
        }
        return Optional.of(new AwsKmsContext(keyArn, keyInfo.publicKey(), keyInfo.signingAlgorithm()));
    }

    @Override
    protected AgentSignature performSign(final String actorId, final AwsKmsContext context,
            final byte[] data) {
        final byte[] sigBytes = client.sign(context.keyArn(), data, context.signingAlgorithm());
        final byte[] pubEncoded = context.publicKey().getEncoded();
        final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
        return new AgentSignature(sigBytes, pubEncoded, keyRef);
    }

    @Override
    protected PublicKey contextPublicKey(final AwsKmsContext context) {
        return context.publicKey();
    }

    @Scheduled(every = "${casehub.ledger.aws-kms.refresh-interval:5m}")
    void refreshCache() {
        LOG.debug("Invalidating AWS KMS context cache");
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
