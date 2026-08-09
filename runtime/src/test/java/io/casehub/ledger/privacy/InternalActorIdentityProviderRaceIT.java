package io.casehub.ledger.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.spi.ActorIdentityProvider;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Concurrent race test for {@code InternalActorIdentityProvider.tokenise()}.
 * Reproduces casehubio/ledger#188: the INSERT loser's ConstraintViolationException
 * dooms the caller's JTA transaction, and the retry SELECT fails.
 */
@QuarkusTest
@TestProfile(InternalActorIdentityProviderRaceIT.Profile.class)
class InternalActorIdentityProviderRaceIT {

    static final int THREAD_COUNT = 8;

    public static class Profile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "tokenise-race-test";
        }
    }

    @Inject
    ActorIdentityProvider provider;

    @Test
    void tokenise_concurrentCallsSameActorId_allSucceedWithSameToken() throws Exception {
        final String actorId = "race-victim-" + UUID.randomUUID();
        final ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        final CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return QuarkusTransaction.requiringNew().call(() ->
                        provider.tokenise(actorId, ActorType.HUMAN));
            }));
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        final List<String> tokens = new ArrayList<>();
        for (final Future<String> f : futures) {
            tokens.add(f.get());
        }

        assertThat(tokens).hasSize(THREAD_COUNT)
                .allSatisfy(t -> assertThat(t).isNotNull().isNotEqualTo(actorId));
        assertThat(tokens.stream().distinct().count())
                .as("All threads should resolve to the same token")
                .isEqualTo(1);
    }
}
