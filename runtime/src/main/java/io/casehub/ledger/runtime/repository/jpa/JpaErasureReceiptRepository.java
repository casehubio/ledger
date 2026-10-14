package io.casehub.ledger.runtime.repository.jpa;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.arc.DefaultBean;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import io.casehub.ledger.jpa.ErasureReceiptLedgerEntry;
import io.casehub.ledger.jpa.LedgerPersistenceUnit;
import io.casehub.ledger.runtime.repository.ErasureReceiptRepository;

/**
 * JPA implementation of {@link ErasureReceiptRepository}.
 *
 * <p>Auto-displaces the {@code @DefaultBean} NoOp via {@code @DefaultBean}.
 */
@ApplicationScoped
@DefaultBean
public class JpaErasureReceiptRepository implements ErasureReceiptRepository {

    @Inject
    @LedgerPersistenceUnit
    EntityManager em;

    @Override
    public List<ErasureReceiptLedgerEntry> findByErasedActorId(
            final String erasedActorId, final String tenancyId) {
        return em.createNamedQuery(
                "ErasureReceiptLedgerEntry.findByErasedActorId", ErasureReceiptLedgerEntry.class)
                .setParameter("erasedActorId", erasedActorId)
                .setParameter("tenancyId", tenancyId)
                .getResultList();
    }

    @Override
    public long countByTenant(final String tenancyId) {
        return em.createNamedQuery(
                "ErasureReceiptLedgerEntry.countByTenant", Long.class)
                .setParameter("tenancyId", tenancyId)
                .getSingleResult();
    }
}
