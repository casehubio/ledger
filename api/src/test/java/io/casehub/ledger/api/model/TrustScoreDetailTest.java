package io.casehub.ledger.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrustScoreDetailTest {

    @Test
    void recordFieldsAreAccessible() {
        final TrustScoreDetail detail = new TrustScoreDetail(
                0.75, TrustAlgorithm.BETA, 12, 0.9, null);

        assertThat(detail.score()).isEqualTo(0.75);
        assertThat(detail.algorithm()).isEqualTo(TrustAlgorithm.BETA);
        assertThat(detail.directAttestationCount()).isEqualTo(12);
        assertThat(detail.dataSufficiency()).isEqualTo(0.9);
        assertThat(detail.mixingWeight()).isNull();
    }

    @Test
    void hybridDetail_hasMixingWeight() {
        final TrustScoreDetail detail = new TrustScoreDetail(
                0.65, TrustAlgorithm.HYBRID, 5, 0.4, 0.7);

        assertThat(detail.algorithm()).isEqualTo(TrustAlgorithm.HYBRID);
        assertThat(detail.mixingWeight()).isEqualTo(0.7);
    }

    @Test
    void unknownAttestationCount_isNegativeOne() {
        final TrustScoreDetail detail = new TrustScoreDetail(
                0.5, TrustAlgorithm.EIGENTRUST, -1, 0.0, null);

        assertThat(detail.directAttestationCount()).isEqualTo(-1);
        assertThat(detail.dataSufficiency()).isEqualTo(0.0);
    }
}
