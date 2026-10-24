package com.akamai.miniwsa.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.akamai.miniwsa.domain.SecurityEvent;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Validation is a responsibility of the core ingestion operation, not the HTTP
 * layer: whatever path an event arrives through (REST, the data generator, a
 * future queue consumer) must pass through here. These tests exercise the
 * service directly, with no Spring context.
 */
class EventIngestionServiceTest {

    // The ValidatorFactory owns resources and must stay open for the lifetime
    // of the Validator it hands out, so we build it once and close it at the end.
    private static ValidatorFactory factory;
    private static Validator validator;

    private final EventIngestionService service = new EventIngestionService(validator);

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void rejectsEventMissingRequiredFields() {
        // eventId blank, clientIp null, timestamp null, rule/action null, ...
        SecurityEvent invalid = new SecurityEvent(
                "",      // eventId  -> blank, invalid
                null,    // timestamp -> required
                0L,      // configId
                null,    // policyId
                null,    // clientIp -> required
                null,    // hostname
                null,    // path
                null,    // method
                0,       // statusCode
                null,    // userAgent
                null,    // rule -> required
                null,    // action -> required
                null,    // geoLocation
                0L,      // requestSize
                0L       // responseSize
        );

        InvalidEventException ex = catchThrowableOfType(
                () -> service.ingest(invalid), InvalidEventException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.errors()).extracting(ValidationError::field)
                .contains("eventId", "clientIp");
    }

    @Test
    void rejectionCarriesAtLeastOneError() {
        SecurityEvent blank = new SecurityEvent(
                null, null, 0L, null, null, null, null, null,
                0, null, null, null, null, 0L, 0L);

        assertThatThrownBy(() -> service.ingest(blank))
                .isInstanceOf(InvalidEventException.class);
    }
}
