package io.casehub.ledger.rest;

import java.util.UUID;

import io.casehub.ledger.api.model.LedgerEntryType;
import io.casehub.ledger.runtime.model.PlainLedgerEntry;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.platform.api.identity.ActorType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class LedgerEntryResourceTest {

    @Inject
    LedgerEntryRepository repository;

    @Test
    void queryBySubjectId_returnsEntries() {
        final UUID subjectId = UUID.randomUUID();
        final var entry = createEntry(subjectId, "actor-1");
        repository.save(entry, "default");

        given()
                .queryParam("arg0", subjectId.toString())
                .queryParam("arg2", "default")
                .when().get("/api/ledger/entries/list-entries")
                .then()
                .statusCode(200)
                .body("entries", hasSize(1))
                .body("entries[0].subjectId", equalTo(subjectId.toString()))
                .body("entries[0].actorId", equalTo("actor-1"))
                .body("totalCount", equalTo(1))
                .body("hasMore", equalTo(false));
    }

    @Test
    void getEntryById_returnsEntry() {
        final UUID subjectId = UUID.randomUUID();
        final var entry = createEntry(subjectId, "actor-2");
        final var saved = repository.save(entry, "default");

        given()
                .queryParam("arg1", "default")
                .when().get("/api/ledger/entries/get-entry/{id}", saved.id)
                .then()
                .statusCode(200)
                .body("id", equalTo(saved.id.toString()))
                .body("actorId", equalTo("actor-2"));
    }

    @Test
    void listEntries_noFilters_returnsEmptyPage() {
        given()
                .queryParam("arg2", "default")
                .when().get("/api/ledger/entries/list-entries")
                .then()
                .statusCode(200)
                .body("entries", hasSize(0))
                .body("totalCount", equalTo(0));
    }

    private PlainLedgerEntry createEntry(final UUID subjectId, final String actorId) {
        final var entry = new PlainLedgerEntry();
        entry.subjectId = subjectId;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = actorId;
        entry.actorType = ActorType.AGENT;
        entry.actorRole = "reviewer";
        return entry;
    }
}
