package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotParserTest {

    private LedgerAttestation attestation(final String snapshotJson) {
        final LedgerAttestation att = new LedgerAttestation();
        att.id = UUID.randomUUID();
        att.verdict = AttestationVerdict.SOUND;
        att.relevanceSnapshot = snapshotJson;
        return att;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void nullOrBlankSnapshot_returnsEmptyMap(final String snapshot) {
        assertThat(SnapshotParser.parse(attestation(snapshot))).isEmpty();
    }

    @Test
    void validJson_returnsParsedMap() {
        final Map<String, Object> result = SnapshotParser.parse(
                attestation("{\"org-distance\": 2, \"team\": \"platform\"}"));

        assertThat(result)
                .containsEntry("org-distance", 2)
                .containsEntry("team", "platform");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not valid json",
            "{malformed",
            "{{}}",
            "[1,2,3]",
            "<html>not json</html>"
    })
    void malformedJson_returnsEmptyMapWithoutThrowing(final String badJson) {
        assertThat(SnapshotParser.parse(attestation(badJson))).isEmpty();
    }
}
