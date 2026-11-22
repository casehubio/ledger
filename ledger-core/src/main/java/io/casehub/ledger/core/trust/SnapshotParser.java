package io.casehub.ledger.core.trust;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.ledger.api.model.LedgerAttestation;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SnapshotParser {

    private static final Logger log = Logger.getLogger(SnapshotParser.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SnapshotParser() {}

    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(LedgerAttestation att) {
        if (att.relevanceSnapshot == null || att.relevanceSnapshot.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(att.relevanceSnapshot, Map.class);
        } catch (Exception ex) {
            log.log(Level.FINE, "Failed to parse relevanceSnapshot for attestation {0}: {1}",
                    new Object[]{att.id, ex.getMessage()});
            return Map.of();
        }
    }
}
