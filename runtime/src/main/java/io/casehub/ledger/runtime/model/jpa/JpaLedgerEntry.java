package io.casehub.ledger.runtime.model.jpa;

import io.casehub.ledger.api.model.LedgerEntry;
import io.casehub.ledger.runtime.model.supplement.JpaComplianceSupplement;
import io.casehub.ledger.runtime.model.supplement.JpaProvenanceSupplement;
import io.casehub.ledger.runtime.service.LedgerTraceListener;
import io.casehub.ledger.runtime.service.identity.LedgerIdentityEnforcementListener;
import io.casehub.platform.api.identity.IdentityBindingStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@NamedQuery(
        name = "LedgerEntry.listAll",
        query = "SELECT e FROM LedgerEntry e")
@NamedQuery(
        name = "LedgerEntry.findAllEvents",
        query = "SELECT e FROM LedgerEntry e WHERE e.entryType = :type")
@NamedQuery(
        name = "LedgerEntry.findEventsByActorId",
        query = "SELECT e FROM LedgerEntry e WHERE e.actorId = :actorId AND e.entryType = :type")
@NamedQuery(
        name = "LedgerEntry.findByTimeRange",
        query = "SELECT e FROM LedgerEntry e WHERE e.occurredAt >= :from AND e.occurredAt <= :to ORDER BY e.occurredAt ASC")
@NamedQuery(
        name = "LedgerEntry.findByIdAndTenancyId",
        query = "SELECT e FROM LedgerEntry e WHERE e.id = :id AND e.tenancyId = :tenancyId")
@NamedQuery(
        name = "LedgerEntry.findSequenceStats",
        query = """
                SELECT NEW io.casehub.ledger.runtime.service.model.SubjectSequenceStats(
                    e.subjectId, e.tenancyId, COUNT(e), MIN(e.sequenceNumber), MAX(e.sequenceNumber)
                )
                FROM LedgerEntry e
                GROUP BY e.subjectId, e.tenancyId
                """)
@Entity(name = "LedgerEntry")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@Table(name = "ledger_entry")
@EntityListeners({LedgerTraceListener.class, LedgerIdentityEnforcementListener.class})
public abstract class JpaLedgerEntry extends LedgerEntry {

    @Transient
    public IdentityBindingStatus pendingIdentityStatus;

    @OneToMany(mappedBy = "jpaLedgerEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<JpaComplianceSupplement> complianceSupplements = new ArrayList<>();

    @OneToMany(mappedBy = "jpaLedgerEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    public List<JpaProvenanceSupplement> provenanceSupplements = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = java.time.Instant.now();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        final JpaLedgerEntry that = (JpaLedgerEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
