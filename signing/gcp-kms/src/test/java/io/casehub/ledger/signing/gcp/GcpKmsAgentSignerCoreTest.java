package io.casehub.ledger.signing.gcp;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class GcpKmsAgentSignerCoreTest {

    @Test
    void initializesClientOnlyForMappedActorsAndReusesIt() {
        try (var clients = mockConstruction(GcpKmsSigningClient.class)) {
            var signer = new GcpKmsAgentSignerCore(Map.of("first", "key-1", "second", "key-2"));
            assertThat(signer.sign("unmapped", new byte[]{1})).isEmpty();
            assertThat(clients.constructed()).isEmpty();

            assertThat(signer.sign("first", new byte[]{1})).isEmpty();
            assertThat(signer.sign("second", new byte[]{1})).isEmpty();
            assertThat(clients.constructed()).hasSize(1);
            verify(clients.constructed().getFirst()).fetchPublicKey("key-1");
            verify(clients.constructed().getFirst()).fetchPublicKey("key-2");
        }
    }
}
