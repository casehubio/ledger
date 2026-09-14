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
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MerkleVerificationResourceTest {

    @Inject
    LedgerEntryRepository repository;

    @Test
    void verify_returnsVerificationResult() {
        final UUID subjectId = UUID.randomUUID();
        final var entry = new PlainLedgerEntry();
        entry.subjectId = subjectId;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-verify";
        entry.actorType = ActorType.AGENT;
        repository.save(entry, "default");

        given()
                .queryParam("arg0", subjectId.toString())
                .queryParam("arg1", "default")
                .when().get("/api/ledger/verification/verify")
                .then()
                .statusCode(200)
                .body("subjectId", equalTo(subjectId.toString()))
                .body("verified", equalTo(true))
                .body("treeRoot", notNullValue());
    }

    @Test
    void inclusionProof_returnsProof() {
        final UUID subjectId = UUID.randomUUID();
        final var entry = new PlainLedgerEntry();
        entry.subjectId = subjectId;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-proof";
        entry.actorType = ActorType.AGENT;
        final var saved = repository.save(entry, "default");

        given()
                .queryParam("arg1", "default")
                .when().get("/api/ledger/verification/inclusion-proof/{entryId}", saved.id)
                .then()
                .statusCode(200)
                .body("entryId", equalTo(saved.id.toString()))
                .body("leafHash", notNullValue())
                .body("treeRoot", notNullValue());
    }
}
