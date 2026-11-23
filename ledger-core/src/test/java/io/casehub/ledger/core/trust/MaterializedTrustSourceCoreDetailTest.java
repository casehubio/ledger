package io.casehub.ledger.core.trust;

import io.casehub.ledger.api.model.ActorTrustScoreBase;
import io.casehub.ledger.api.model.ScoreType;
import io.casehub.ledger.api.model.TrustAlgorithm;
import io.casehub.ledger.api.model.TrustScoreDetail;
import io.casehub.ledger.api.spi.ActorTrustScoreRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaterializedTrustSourceCoreDetailTest {

    @Test
    void globalScoreDetail_populatesAttestationCountAndSufficiency() {
        final ActorTrustScoreRepository repo = mock(ActorTrustScoreRepository.class);
        final ActorTrustScoreBase score = new ActorTrustScoreBase();
        score.actorId = "alice";
        score.scoreType = ScoreType.GLOBAL;
        score.trustScore = 0.75;
        score.attestationPositive = 7;
        score.attestationNegative = 3;
        score.lastComputedAt = Instant.now();
        when(repo.findByActorId("alice")).thenReturn(Optional.of(score));

        final MaterializedTrustSourceCore core = new MaterializedTrustSourceCore(repo);
        final Optional<TrustScoreDetail> detail = core.globalScoreDetail("alice");

        assertThat(detail).isPresent();
        assertThat(detail.get().score()).isEqualTo(0.75);
        assertThat(detail.get().algorithm()).isEqualTo(TrustAlgorithm.BETA);
        assertThat(detail.get().directAttestationCount()).isEqualTo(10);
        assertThat(detail.get().dataSufficiency()).isCloseTo(1.0, within(0.001));
        assertThat(detail.get().mixingWeight()).isNull();
    }

    @Test
    void globalScoreDetail_lowAttestationCount_lowSufficiency() {
        final ActorTrustScoreRepository repo = mock(ActorTrustScoreRepository.class);
        final ActorTrustScoreBase score = new ActorTrustScoreBase();
        score.actorId = "bob";
        score.scoreType = ScoreType.GLOBAL;
        score.trustScore = 0.6;
        score.attestationPositive = 2;
        score.attestationNegative = 1;
        score.lastComputedAt = Instant.now();
        when(repo.findByActorId("bob")).thenReturn(Optional.of(score));

        final MaterializedTrustSourceCore core = new MaterializedTrustSourceCore(repo);
        final Optional<TrustScoreDetail> detail = core.globalScoreDetail("bob");

        assertThat(detail).isPresent();
        assertThat(detail.get().directAttestationCount()).isEqualTo(3);
        assertThat(detail.get().dataSufficiency()).isCloseTo(0.3, within(0.001));
    }

    @Test
    void globalScoreDetail_noScore_returnsEmpty() {
        final ActorTrustScoreRepository repo = mock(ActorTrustScoreRepository.class);
        when(repo.findByActorId("unknown")).thenReturn(Optional.empty());

        final MaterializedTrustSourceCore core = new MaterializedTrustSourceCore(repo);
        assertThat(core.globalScoreDetail("unknown")).isEmpty();
    }
}
