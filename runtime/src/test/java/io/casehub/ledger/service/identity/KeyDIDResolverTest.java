package io.casehub.ledger.service.identity;

import io.casehub.platform.identity.KeyDIDResolver;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class KeyDIDResolverTest {

    private final KeyDIDResolver resolver = new KeyDIDResolver();

    private String buildDIDKey(byte[] rawKey) {
        byte[] multicodec = new byte[rawKey.length + 2];
        multicodec[0] = (byte) 0xed;
        multicodec[1] = 0x01;
        System.arraycopy(rawKey, 0, multicodec, 2, rawKey.length);
        return "did:key:z" + Base64.getUrlEncoder().withoutPadding().encodeToString(multicodec);
    }

    private static byte[] rawEd25519Key(byte[] spki) {
        return Arrays.copyOfRange(spki, spki.length - 32, spki.length);
    }

    @Test
    void resolvesValidDIDKeyToDocument() throws Exception {
        var gen = KeyPairGenerator.getInstance("Ed25519");
        var keyPair = gen.generateKeyPair();
        var did = buildDIDKey(rawEd25519Key(keyPair.getPublic().getEncoded()));

        var result = resolver.resolve("test-actor", did);
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(did);
        assertThat(result.get().verificationMethods()).hasSize(1);
        assertThat(result.get().verificationMethods().get(0).publicKeyBytes())
            .isEqualTo(keyPair.getPublic().getEncoded());
    }

    @Test
    void alsoKnownAsContainsActorId() throws Exception {
        var gen = KeyPairGenerator.getInstance("Ed25519");
        var did = buildDIDKey(rawEd25519Key(gen.generateKeyPair().getPublic().getEncoded()));
        assertThat(resolver.resolve("test-actor", did).get().alsoKnownAs())
            .containsExactly("test-actor");
    }

    @Test
    void alsoKnownAsEmptyWhenActorIdNull() throws Exception {
        var gen = KeyPairGenerator.getInstance("Ed25519");
        var did = buildDIDKey(rawEd25519Key(gen.generateKeyPair().getPublic().getEncoded()));
        assertThat(resolver.resolve(null, did).get().alsoKnownAs()).isEmpty();
    }

    @Test
    void returnsEmptyForNonDIDKeyMethod() {
        assertThat(resolver.resolve("test-actor", "did:web:example.com")).isEmpty();
        assertThat(resolver.resolve("test-actor", "did:ethr:0xabc")).isEmpty();
    }

    @Test
    void returnsEmptyForMalformedKey() {
        assertThat(resolver.resolve("test-actor", "did:key:NOT_BASE64URL!!")).isEmpty();
    }

    @Test
    void returnsEmptyForNull() {
        assertThat(resolver.resolve("test-actor", null)).isEmpty();
    }
}
