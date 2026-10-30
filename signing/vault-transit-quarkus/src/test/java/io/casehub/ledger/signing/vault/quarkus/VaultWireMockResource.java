package io.casehub.ledger.signing.vault.quarkus;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class VaultWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return Map.of("casehub.ledger.vault-transit.address",
                "http://localhost:" + server.port());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public void inject(final TestInjector testInjector) {
        testInjector.injectIntoFields(server, new TestInjector.AnnotatedAndMatchesType(
                InjectWireMock.class, WireMockServer.class));
    }
}
