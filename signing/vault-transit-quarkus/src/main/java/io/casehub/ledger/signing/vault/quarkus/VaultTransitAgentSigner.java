package io.casehub.ledger.signing.vault.quarkus;

import java.net.http.HttpClient;
import java.security.PublicKey;
import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.casehub.ledger.runtime.service.AbstractCachingAgentSigner;
import io.casehub.ledger.runtime.service.AgentKeyRotatedEvent;
import io.casehub.ledger.runtime.service.AgentSignature;
import io.casehub.ledger.signing.vault.AppRoleVaultTokenSource;
import io.casehub.ledger.signing.vault.KubernetesVaultTokenSource;
import io.casehub.ledger.signing.vault.StaticVaultTokenSource;
import io.casehub.ledger.signing.vault.VaultAuthenticationException;
import io.casehub.ledger.signing.vault.VaultTokenSource;
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
 * <p><strong>Auth:</strong> Supports TOKEN, APPROLE, and KUBERNETES auth methods.
 * Configure via {@code casehub.ledger.vault-transit.auth.method}.
 * 403 responses trigger token invalidation and a single retry.
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
    private final VaultTokenSource tokenSource;

    @Inject
    public VaultTransitAgentSigner(final VaultTransitConfig config) {
        this.config = config;

        // Create shared HttpClient and ObjectMapper for all auth calls
        final HttpClient httpClient = HttpClient.newBuilder().build();
        final ObjectMapper objectMapper = new ObjectMapper();

        // Create token source based on auth method
        this.tokenSource = createTokenSource(config, httpClient, objectMapper);

        // Create signing client (does NOT hold a token — receives per-call from tokenSource)
        this.client = new VaultTransitSigningClient(
                new VaultTransitSigningConfig(config.address(), config.keyMapping()),
                httpClient,
                objectMapper);
    }

    // Visible for testing — allows injecting a pre-configured client and token source
    VaultTransitAgentSigner(final VaultTransitConfig config, final VaultTransitSigningClient client,
            final VaultTokenSource tokenSource) {
        this.config = config;
        this.client = client;
        this.tokenSource = tokenSource;
    }

    private static VaultTokenSource createTokenSource(final VaultTransitConfig config,
            final HttpClient httpClient, final ObjectMapper objectMapper) {
        final VaultTransitConfig.AuthConfig authConfig = config.auth();
        return switch (authConfig.method()) {
            case TOKEN -> {
                final String token = authConfig.token()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.token required when auth.method=token"));
                yield new StaticVaultTokenSource(token);
            }
            case APPROLE -> {
                final String roleId = authConfig.roleId()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.role-id required when auth.method=approle"));
                final String secretId = authConfig.secretId()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.secret-id required when auth.method=approle"));
                final String mountPath = authConfig.mountPath().orElse("approle");
                yield new AppRoleVaultTokenSource(config.address(), roleId, secretId, mountPath,
                        httpClient, objectMapper, java.time.Clock.systemUTC());
            }
            case KUBERNETES -> {
                final String role = authConfig.role()
                        .orElseThrow(() -> new IllegalStateException(
                                "casehub.ledger.vault-transit.auth.role required when auth.method=kubernetes"));
                final java.nio.file.Path jwtPath = java.nio.file.Path.of(authConfig.jwtPath());
                final String mountPath = authConfig.mountPath().orElse("kubernetes");
                yield new KubernetesVaultTokenSource(config.address(), role, jwtPath, mountPath,
                        httpClient, objectMapper, java.time.Clock.systemUTC());
            }
        };
    }

    @Override
    protected Optional<VaultTransitContext> loadContext(final String actorId) {
        final String keyName = config.keyMapping().get(actorId);
        if (keyName == null) {
            LOG.debugf("No Vault Transit key configured for actor %s — skipping signing", actorId);
            return Optional.empty();
        }

        // Attempt to fetch public key with 403-retry
        try {
            final String token = tokenSource.token();
            final PublicKey publicKey = client.fetchPublicKey(token, keyName);
            return Optional.of(new VaultTransitContext(keyName, publicKey));
        } catch (final VaultAuthenticationException e) {
            LOG.debugf("Vault 403 on key fetch for %s — invalidating token and retrying", actorId);
            tokenSource.invalidate();
            // Retry once with fresh token
            final String freshToken = tokenSource.token();
            final PublicKey publicKey = client.fetchPublicKey(freshToken, keyName);
            return Optional.of(new VaultTransitContext(keyName, publicKey));
        }
    }

    @Override
    protected AgentSignature performSign(final String actorId, final VaultTransitContext context,
            final byte[] data) {
        // Attempt to sign with 403-retry
        try {
            final String token = tokenSource.token();
            final byte[] sigBytes = client.sign(token, context.keyName(), data);
            final byte[] pubEncoded = context.publicKey().getEncoded();
            final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
            return new AgentSignature(sigBytes, pubEncoded, keyRef);
        } catch (final VaultAuthenticationException e) {
            LOG.debugf("Vault 403 on sign for %s — invalidating token and retrying", actorId);
            tokenSource.invalidate();
            // Retry once with fresh token — rethrow on second failure
            final String freshToken = tokenSource.token();
            final byte[] sigBytes = client.sign(freshToken, context.keyName(), data);
            final byte[] pubEncoded = context.publicKey().getEncoded();
            final String keyRef = AgentSignature.computeKeyRef(pubEncoded);
            return new AgentSignature(sigBytes, pubEncoded, keyRef);
        }
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
