package io.casehub.ledger.api.model;

import io.casehub.platform.api.identity.ActorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerEntryCanonicalBytesTest {

    static class TestEntry extends LedgerEntry {
    }

    private TestEntry baseEntry() {
        TestEntry e = new TestEntry();
        e.id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        e.subjectId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        e.tenancyId = "test-tenant";
        e.sequenceNumber = 1;
        e.entryType = LedgerEntryType.EVENT;
        e.actorId = "actor-1";
        e.actorType = ActorType.HUMAN;
        e.actorRole = "reviewer";
        e.occurredAt = Instant.parse("2026-01-01T00:00:00.000Z");
        return e;
    }

    @Test
    void nullDomainDataProducesSameHashAsBefore() {
        TestEntry e = baseEntry();
        e.domainData = null;

        byte[] bytes = e.canonicalBytes();
        String canonical = new String(bytes);

        assertThat(canonical).doesNotContain("domainData");
        assertThat(e.domainData).isNull();

        TestEntry e2 = baseEntry();
        assertThat(e2.canonicalBytes()).isEqualTo(bytes);
    }

    @Test
    void emptyDomainDataProducesSameHashAsNull() {
        TestEntry withNull = baseEntry();
        withNull.domainData = null;

        TestEntry withEmpty = baseEntry();
        withEmpty.domainData = Map.of();

        assertThat(withEmpty.canonicalBytes()).isEqualTo(withNull.canonicalBytes());
    }

    @Test
    void populatedDomainDataIncludedInCanonicalBytes() {
        TestEntry without = baseEntry();
        without.domainData = null;

        TestEntry with = baseEntry();
        with.domainData = Map.of("key", "value");

        assertThat(with.canonicalBytes()).isNotEqualTo(without.canonicalBytes());
    }

    @Test
    void domainDataKeyOrderDoesNotAffectHash() {
        TestEntry e1 = baseEntry();
        Map<String, Object> data1 = new LinkedHashMap<>();
        data1.put("alpha", 1L);
        data1.put("beta", 2L);
        e1.domainData = data1;

        TestEntry e2 = baseEntry();
        Map<String, Object> data2 = new LinkedHashMap<>();
        data2.put("beta", 2L);
        data2.put("alpha", 1L);
        e2.domainData = data2;

        assertThat(e1.canonicalBytes()).isEqualTo(e2.canonicalBytes());
    }

    @Test
    void nestedDomainDataProducesDeterministicHash() {
        TestEntry e1 = baseEntry();
        Map<String, Object> nested1 = new LinkedHashMap<>();
        nested1.put("z", "last");
        nested1.put("a", "first");
        e1.domainData = Map.of("outer", nested1, "simple", "value");

        TestEntry e2 = baseEntry();
        Map<String, Object> nested2 = new LinkedHashMap<>();
        nested2.put("a", "first");
        nested2.put("z", "last");
        e2.domainData = Map.of("simple", "value", "outer", nested2);

        assertThat(e1.canonicalBytes()).isEqualTo(e2.canonicalBytes());
    }
}
