package io.casehub.ledger.rest;

import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerExceptionMapperTest {

    private final LedgerExceptionMapper mapper = new LedgerExceptionMapper();

    @Test
    void webApplicationException_preservesOriginalStatus() {
        final var exception = new WebApplicationException("Unprocessable", 422);

        final Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(422);
    }

    @Test
    void notFoundException_preservesOriginalStatus() {
        final var exception = new NotFoundException("Not here");

        final Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void clientErrorException_preservesOriginalStatus() {
        final var exception = new ClientErrorException("Bad", 409);

        final Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    void ledgerNotFoundException_returns404() {
        final var exception = new LedgerNotFoundException("entry not found");

        final Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void illegalArgumentException_returns400() {
        final var exception = new IllegalArgumentException("bad input");

        final Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void illegalStateException_returns409() {
        final var exception = new IllegalStateException("conflict");

        final Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(409);
    }

    @Test
    void unknownRuntimeException_returns500() {
        final var exception = new RuntimeException("unexpected");

        final Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(500);
    }
}
