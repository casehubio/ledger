package io.casehub.ledger.api.spi;

import io.casehub.ledger.api.model.TrustAlgorithm;
import io.casehub.ledger.api.model.TrustScoreDetail;
import org.junit.jupiter.api.Test;

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

    private TrustScoreSource sourceWithCapability(final OptionalDouble capabilityResult) {
        return new TrustScoreSource() {
            @Override
            public OptionalDouble globalScore(String a)                                  {return OptionalDouble.empty();}

            @Override
            public OptionalDouble capabilityScore(String a, String c)                    {return capabilityResult;}

            @Override
            public OptionalDouble dimensionScore(String a, String d)                     {return OptionalDouble.empty();}

            @Override
            public OptionalDouble capabilityDimensionScore(String a, String c, String d) {return OptionalDouble.empty();}

            @Override
            public int decisionCount(String a, String c)                                 {return 0;}

            @Override
            public Map<String, Double> allCapabilityScores(String a)                     {return Map.of();}

            @Override
            public Map<String, Double> allDimensionScores(String a)                      {return Map.of();}

            @Override
            public Map<String, Double> qualityScores(String a, String c)                 {return Map.of();}
        };
    }

    private TrustScoreSource sourceWithDimension(final OptionalDouble dimensionResult) {
        return new TrustScoreSource() {
            @Override
            public OptionalDouble globalScore(String a)                                  {return OptionalDouble.empty();}

            @Override
            public OptionalDouble capabilityScore(String a, String c)                    {return OptionalDouble.empty();}

            @Override
            public OptionalDouble dimensionScore(String a, String d)                     {return dimensionResult;}

            @Override
            public OptionalDouble capabilityDimensionScore(String a, String c, String d) {return OptionalDouble.empty();}

            @Override
            public int decisionCount(String a, String c)                                 {return 0;}

            @Override
            public Map<String, Double> allCapabilityScores(String a)                     {return Map.of();}

            @Override
            public Map<String, Double> allDimensionScores(String a)                      {return Map.of();}

            @Override
            public Map<String, Double> qualityScores(String a, String c)                 {return Map.of();}
        };
    }

    @Test
    void capabilityScoreDetail_withScore_returnsBetaDefault() {
        final Optional<TrustScoreDetail> detail = sourceWithCapability(OptionalDouble.of(0.65))
                                                          .capabilityScoreDetail("alice", "review");

        assertThat(detail).isPresent();
        assertThat(detail.get().score()).isEqualTo(0.65);
        assertThat(detail.get().algorithm()).isEqualTo(TrustAlgorithm.BETA);
        assertThat(detail.get().directAttestationCount()).isEqualTo(-1);
    }

    @Test
    void capabilityScoreDetail_noScore_returnsEmpty() {
        assertThat(sourceWithCapability(OptionalDouble.empty())
                           .capabilityScoreDetail("unknown", "review")).isEmpty();
    }

    @Test
    void dimensionScoreDetail_withScore_returnsBetaDefault() {
        final Optional<TrustScoreDetail> detail = sourceWithDimension(OptionalDouble.of(0.72))
                                                          .dimensionScoreDetail("alice", "thoroughness");

        assertThat(detail).isPresent();
        assertThat(detail.get().score()).isEqualTo(0.72);
        assertThat(detail.get().algorithm()).isEqualTo(TrustAlgorithm.BETA);
    }

    @Test
    void dimensionScoreDetail_noScore_returnsEmpty() {
        assertThat(sourceWithDimension(OptionalDouble.empty())
                           .dimensionScoreDetail("unknown", "thoroughness")).isEmpty();
    }


}
