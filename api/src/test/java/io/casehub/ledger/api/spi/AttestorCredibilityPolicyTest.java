package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.model.CredibilityFlag;
import io.casehub.ledger.api.spi.AttestorCredibilityPolicy.CredibilityAssessment;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AttestorCredibilityPolicyTest {

    @Test
    void neutral_hasFullWeight_noFlags() {
        assertThat(CredibilityAssessment.NEUTRAL.weight()).isEqualTo(1.0);
        assertThat(CredibilityAssessment.NEUTRAL.reason()).isNull();
        assertThat(CredibilityAssessment.NEUTRAL.flags()).isEmpty();
    }

    @Test
    void assessBatch_defaultDelegatesToAssess() {
        AttestorCredibilityPolicy policy = attestorId ->
                new CredibilityAssessment(0.8, "test", Set.of(CredibilityFlag.LOW_AGREEMENT));

        var result = policy.assessBatch(Set.of("agent-a", "agent-b"));

        assertThat(result).hasSize(2);
        assertThat(result.get("agent-a").weight()).isEqualTo(0.8);
        assertThat(result.get("agent-a").flags()).containsExactly(CredibilityFlag.LOW_AGREEMENT);
        assertThat(result.get("agent-b").weight()).isEqualTo(0.8);
    }

    @Test
    void assessBatch_canBeOverridden() {
        AttestorCredibilityPolicy policy = new AttestorCredibilityPolicy() {
            @Override
            public CredibilityAssessment assess(String attestorId) {
                return CredibilityAssessment.NEUTRAL;
            }

            @Override
            public java.util.Map<String, CredibilityAssessment> assessBatch(Set<String> attestorIds) {
                var result = new java.util.LinkedHashMap<String, CredibilityAssessment>();
                for (String id : attestorIds) {
                    result.put(id, new CredibilityAssessment(0.3, "batch-custom", Set.of()));
                }
                return result;
            }
        };

        var result = policy.assessBatch(Set.of("x"));
        assertThat(result.get("x").weight()).isEqualTo(0.3);
        assertThat(result.get("x").reason()).isEqualTo("batch-custom");
    }
}
