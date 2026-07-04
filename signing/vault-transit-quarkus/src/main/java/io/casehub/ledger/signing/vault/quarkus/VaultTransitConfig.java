package io.casehub.ledger.signing.vault.quarkus;

import java.util.Map;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Quarkus configuration for the Vault Transit signing adapter.
 * All keys are under the {@code casehub.ledger.vault-transit} prefix.
 *
 * <p>Bridges Quarkus {@code @ConfigMapping} to the pure Java
 * {@link io.casehub.ledger.signing.vault.VaultTransitSigningConfig}.
 */
@ConfigMapping(prefix = "casehub.ledger.vault-transit")
public interface VaultTransitConfig {

    /**
     * Base URL of the Vault instance, e.g. {@code http://localhost:8200}.
     */
    @WithDefault("http://localhost:8200")
    String address();

    /**
     * Map of actorId to Vault Transit key name.
     * Example: {@code casehub.ledger.vault-transit.key-mapping."claude:reviewer@v1"=reviewer-key}
     */
    Map<String, String> keyMapping();

    /**
     * How often to invalidate the public-key cache and re-fetch from Vault.
     * Expressed as a Quarkus duration string, e.g. {@code "5m"}, {@code "24h"}.
     */
    @WithDefault("5m")
    String refreshInterval();

    /**
     * Vault authentication configuration.
     */
    AuthConfig auth();

    /**
     * Vault authentication method configuration.
     */
    interface AuthConfig {

        /**
         * Authentication method: TOKEN, APPROLE, KUBERNETES, or JWT.
         */
        @WithDefault("token")
        AuthMethod method();

        /**
         * Static token for TOKEN auth method.
         */
        Optional<String> token();

        /**
         * Role ID for AppRole auth method.
         */
        Optional<String> roleId();

        /**
         * Secret ID for AppRole auth method.
         */
        Optional<String> secretId();

        /**
         * Role for Kubernetes or JWT auth methods.
         */
        Optional<String> role();

        /**
         * Path to JWT file for KUBERNETES or JWT auth methods.
         */
        Optional<String> jwtPath();

        /**
         * Static JWT string for JWT auth method. Mutually exclusive with jwt-path.
         * Quarkus config substitution applies: {@code ${MY_JWT_TOKEN}} resolves from environment.
         */
        Optional<String> jwt();

        /**
         * Vault mount path for AppRole, Kubernetes, or JWT auth.
         * Defaults to the auth method name if not specified.
         * For Vault OIDC auth backends, use {@code mountPath=oidc} with {@code method=jwt}
         * — the JWT and OIDC auth methods share the same login API.
         */
        Optional<String> mountPath();
    }

    /**
     * Vault authentication methods supported by this adapter.
     */
    enum AuthMethod { TOKEN, APPROLE, KUBERNETES, JWT }
}
