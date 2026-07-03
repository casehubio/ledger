package io.casehub.ledger.signing.vault;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class AppRoleVaultTokenSource extends LoginBasedVaultTokenSource {

    private final String roleId;
    private final String secretId;
    private final String mountPath;

    public AppRoleVaultTokenSource(final String vaultAddress, final String roleId,
            final String secretId, final String mountPath,
            final HttpClient http, final ObjectMapper mapper, final Clock clock) {
        super(vaultAddress, http, mapper, clock);
        this.roleId = Objects.requireNonNull(roleId);
        this.secretId = Objects.requireNonNull(secretId);
        this.mountPath = mountPath != null ? mountPath : "approle";
    }

    @Override
    protected String loginPath() {
        return "/v1/auth/" + mountPath + "/login";
    }

    @Override
    protected String loginRequestBody() {
        return "{\"role_id\":\"" + roleId + "\",\"secret_id\":\"" + secretId + "\"}";
    }
}
