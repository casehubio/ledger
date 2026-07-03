package io.casehub.ledger.signing.vault;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class StaticVaultTokenSourceTest {

    @Test
    void token_returnsConfiguredValue() {
        final VaultTokenSource source = new StaticVaultTokenSource("hvs.my-token");
        assertThat(source.token()).isEqualTo("hvs.my-token");
    }

    @Test
    void token_returnsSameValueOnRepeatedCalls() {
        final VaultTokenSource source = new StaticVaultTokenSource("hvs.stable");
        assertThat(source.token()).isEqualTo("hvs.stable");
        assertThat(source.token()).isEqualTo("hvs.stable");
    }

    @Test
    void invalidate_isNoOp() {
        final VaultTokenSource source = new StaticVaultTokenSource("hvs.my-token");
        source.invalidate();
        assertThat(source.token()).isEqualTo("hvs.my-token");
    }
}
