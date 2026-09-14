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
class AttestationResourceTest {

    @Inject
    LedgerEntryRepository repository;

    @Test
    void createAndListAttestations() {
        final UUID subjectId = UUID.randomUUID();
        final var entry = new PlainLedgerEntry();
        entry.subjectId = subjectId;
        entry.entryType = LedgerEntryType.EVENT;
        entry.actorId = "actor-attest";
        entry.actorType = ActorType.AGENT;
        final var saved = repository.save(entry, "default");

        given()
                .contentType("application/json")
                .queryParam("arg1", "default")
                .body("""
                        {
                          "entryId": "%s",
                          "attestorId": "reviewer-1",
                          "attestorType": "AGENT",
                          "attestorRole": "reviewer",
                          "verdict": "SOUND",
                          "evidence": "Looks correct",
                          "confidence": 0.95,
                          "capabilityTag": "*"
                        }
                        """.formatted(saved.id))
                .when().post("/api/ledger/attestations/create-attestation")
                .then()
                .statusCode(200)
                .body("attestorId", equalTo("reviewer-1"))
                .body("verdict", equalTo("SOUND"));

        given()
                .queryParam("arg1", "default")
                .when().get("/api/ledger/attestations/list-attestations/{entryId}", saved.id)
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].attestorId", equalTo("reviewer-1"));
    }

    @Test
    void createAttestation_entryNotFound_returns400() {
        given()
                .contentType("application/json")
                .queryParam("arg1", "default")
                .body("""
                        {
                          "entryId": "%s",
                          "attestorId": "reviewer-1",
                          "attestorType": "AGENT",
                          "verdict": "SOUND",
                          "confidence": 0.9,
                          "capabilityTag": "*"
                        }
                        """.formatted(UUID.randomUUID()))
                .when().post("/api/ledger/attestations/create-attestation")
                .then()
                .statusCode(400);
    }
}
