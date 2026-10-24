package com.akamai.miniwsa.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The catch-all handler turns any unexpected exception into a clean 500 that
 * does not leak internal details (messages, stack traces).
 */
class IngestionExceptionHandlerTest {

    private final IngestionExceptionHandler handler = new IngestionExceptionHandler();

    @Test
    void unexpectedExceptionBecomesCleanInternalServerError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("secret internal boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().errors()).hasSize(1);
        ValidationError error = response.getBody().errors().get(0);
        assertThat(error.message()).isEqualTo("Internal server error");
        assertThat(error.field()).isNull();
        // the real cause must not leak to the client
        assertThat(error.message()).doesNotContain("boom");
    }
}
