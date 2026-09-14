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
                .queryParam("subjectId", subjectId.toString())
                .queryParam("tenancyId", "default")
                .when().get("/api/v1/ledger/entries")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].subjectId", equalTo(subjectId.toString()))
                .body("[0].actorId", equalTo("actor-1"));
    }

    @Test
    void getEntryById_returnsEntry() {
        final UUID subjectId = UUID.randomUUID();
        final var entry = createEntry(subjectId, "actor-2");
        final var saved = repository.save(entry, "default");

        given()
                .queryParam("tenancyId", "default")
                .when().get("/api/v1/ledger/entries/{id}", saved.id)
                .then()
                .statusCode(200)
                .body("id", equalTo(saved.id.toString()))
                .body("actorId", equalTo("actor-2"));
    }

    @Test
    void getEntryById_notFound_returns404() {
        given()
                .queryParam("tenancyId", "default")
                .when().get("/api/v1/ledger/entries/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    void queryEntries_noParams_returns400() {
        given()
                .queryParam("tenancyId", "default")
                .when().get("/api/v1/ledger/entries")
                .then()
                .statusCode(400);
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
