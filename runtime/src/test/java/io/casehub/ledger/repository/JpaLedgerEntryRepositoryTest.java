package io.casehub.ledger.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.runtime.repository.jpa.JpaLedgerEntryRepository;

class JpaLedgerEntryRepositoryTest {

    @Test
    void saveAttestation_usesRequiresNewTransaction() throws Exception {
        final Method method = JpaLedgerEntryRepository.class.getMethod(
                "saveAttestation", LedgerAttestation.class, String.class);
        final Transactional txAnno = method.getAnnotation(Transactional.class);

        assertThat(txAnno)
                .as("@Transactional must be present on saveAttestation")
                .isNotNull();
        assertThat(txAnno.value())
                .as("saveAttestation must use REQUIRES_NEW to isolate from caller's transaction")
                .isEqualTo(Transactional.TxType.REQUIRES_NEW);
    }
}
