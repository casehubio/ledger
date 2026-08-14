package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.model.CredibilityFlag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public interface AttestorCredibilityPolicy {

    CredibilityAssessment assess(String attestorId);

    default Map<String, CredibilityAssessment> assessBatch(Set<String> attestorIds) {
        Map<String, CredibilityAssessment> result = new LinkedHashMap<>();
        for (String id : attestorIds) {
            result.put(id, assess(id));
        }
        return result;
    }

    record CredibilityAssessment(
            double weight,
            String reason,
            Set<CredibilityFlag> flags) {

        public static final CredibilityAssessment NEUTRAL =
                new CredibilityAssessment(1.0, null, Set.of());
    }
}
