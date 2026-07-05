package io.casehub.ledger.runtime.service;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import io.casehub.ledger.api.model.AuditRecord;
import io.casehub.ledger.api.spi.LedgerAppender;
import io.casehub.ledger.api.spi.ReactiveLedgerAppender;

/**
 * Default reactive bridge — wraps {@link LedgerAppender} on the Mutiny worker pool.
 *
 * <p>{@code @DefaultBean} with no {@code @IfBuildProperty} gate. This bridge has no
 * Hibernate Reactive dependency and must be active under all profiles. Per the
 * {@code reactive-spi-bridge-default-bean} platform protocol: bridges are always active;
 * native async adapters activate via {@code @Alternative @Priority(N)}.
 *
 * <p>Callers on the Vert.x event loop use this safely — the blocking delegate runs on the
 * worker pool, not the calling thread.
 */
@DefaultBean
@ApplicationScoped
public class DefaultReactiveLedgerAppender implements ReactiveLedgerAppender {

    @Inject
    LedgerAppender blocking;

    @Override
    public Uni<UUID> append(final AuditRecord record, final String tenancyId) {
        return Uni.createFrom()
                .item(() -> blocking.append(record, tenancyId))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
