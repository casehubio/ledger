package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.model.TrustAlgorithm;
import io.casehub.ledger.api.model.TrustScoreDetail;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class TrustScoreSourceDefaultMethodTest {

    private TrustScoreSource source(final OptionalDouble globalResult) {
        return new TrustScoreSource() {
            @Override public OptionalDouble globalScore(String actorId) { return globalResult; }
            @Override public OptionalDouble capabilityScore(String a, String c) { return OptionalDouble.empty(); }
            @Override public OptionalDouble dimensionScore(String a, String d) { return OptionalDouble.empty(); }
            @Override public OptionalDouble capabilityDimensionScore(String a, String c, String d) { return OptionalDouble.empty(); }
            @Override public int decisionCount(String a, String c) { return 0; }
            @Override public Map<String, Double> allCapabilityScores(String a) { return Map.of(); }
            @Override public Map<String, Double> allDimensionScores(String a) { return Map.of(); }
            @Override public Map<String, Double> qualityScores(String a, String c) { return Map.of(); }
        };
    }

    @Test
    void globalScoreDetail_withScore_returnsBetaDefault() {
        final Optional<TrustScoreDetail> detail = source(OptionalDouble.of(0.8))
                .globalScoreDetail("alice");

        assertThat(detail).isPresent();
        assertThat(detail.get().score()).isEqualTo(0.8);
        assertThat(detail.get().algorithm()).isEqualTo(TrustAlgorithm.BETA);
        assertThat(detail.get().directAttestationCount()).isEqualTo(-1);
        assertThat(detail.get().dataSufficiency()).isEqualTo(1.0);
        assertThat(detail.get().mixingWeight()).isNull();
    }

    @Test
    void globalScoreDetail_noScore_returnsEmpty() {
        final Optional<TrustScoreDetail> detail = source(OptionalDouble.empty())
                .globalScoreDetail("unknown");

        assertThat(detail).isEmpty();
    }
}
