package com.akamai.miniwsa.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.EnrichedEvent;
import com.akamai.miniwsa.domain.GeoLocation;
import com.akamai.miniwsa.domain.Rule;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.domain.SecurityEvent;
import com.akamai.miniwsa.domain.Severity;
import com.akamai.miniwsa.enrichment.ThreatScorer;
import com.akamai.miniwsa.storage.InMemoryEventRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Validation, enrichment, and storage are responsibilities of the core
 * ingestion operation, not the HTTP layer: whatever path an event arrives
 * through (REST, the data generator, a future queue consumer) must pass through
 * here. These tests exercise the service directly, with no Spring context.
 */
class EventIngestionServiceTest {

    // The ValidatorFactory owns resources and must stay open for the lifetime
    // of the Validator it hands out, so we build it once and close it at the end.
    private static ValidatorFactory factory;
    private static Validator validator;

    private final ThreatScorer scorer = new ThreatScorer();
    private final InMemoryEventRepository repository = new InMemoryEventRepository();
    private final EventIngestionService service =
            new EventIngestionService(validator, scorer, repository);

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
    void enrichesAndStoresValidEvent() {
        SecurityEvent event = validEvent();

        Instant before = Instant.now();
        EnrichedEvent enriched = service.ingest(event);
        Instant after = Instant.now();

        assertThat(enriched.event()).isEqualTo(event);
        assertThat(enriched.attackType()).isEqualTo("SQL/Command Injection");
        assertThat(enriched.threatScore()).isEqualTo(75); // CRITICAL 40 + DENY 20 + /login 15
        assertThat(enriched.receivedAt()).isNotNull();
        assertThat(enriched.receivedAt()).isBetween(before, after);

        assertThat(repository.findAll()).containsExactly(enriched);
    }

    @Test
    void repeatOffenderFromSameIpEarnsBonusBeyondFive() {
        EnrichedEvent sixth = null;
        EnrichedEvent seventh = null;
        for (int i = 1; i <= 7; i++) {
            EnrichedEvent enriched = service.ingest(eventWithId("evt-" + i));
            if (i == 6) {
                sixth = enriched;
            } else if (i == 7) {
                seventh = enriched;
            }
        }

        // 6th ingest sees 5 prior events from the IP -> not "more than 5" -> base 75
        assertThat(sixth.threatScore()).isEqualTo(75);
        // 7th ingest sees 6 prior -> repeat-offender bonus -> 75 + 15 = 90
        assertThat(seventh.threatScore()).isEqualTo(90);
    }

    @Test
    void duplicateEventIdIsUpsertedAndDoesNotInflateRepeatOffender() {
        // At-least-once edge delivery means replays are expected: ingesting the
        // same eventId must be idempotent (one stored record), and replays must
        // not inflate the repeat-offender count.
        SecurityEvent event = eventWithId("evt-dup");
        EnrichedEvent last = null;
        for (int i = 0; i < 7; i++) {
            last = service.ingest(event);
        }

        assertThat(repository.findAll()).hasSize(1);
        assertThat(last.threatScore()).isEqualTo(75); // base only; replays never exceed 5
    }

    @Test
    void batchWithAnyInvalidEventStoresNothing() {
        // All-or-nothing: a single invalid event rejects the whole batch, and no
        // event (not even the valid ones) is stored.
        SecurityEvent valid = eventWithId("evt-ok");
        SecurityEvent invalid = new SecurityEvent(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.ingest(List.of(valid, invalid)))
                .isInstanceOf(InvalidEventException.class);

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void rejectedEventIsNotStored() {
        SecurityEvent invalid = new SecurityEvent(
                null, null, 0L, null, null, null, null, null,
                0, null, null, null, null, 0L, 0L);

        assertThatThrownBy(() -> service.ingest(invalid))
                .isInstanceOf(InvalidEventException.class);

        assertThat(repository.findAll()).isEmpty();
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

    private static SecurityEvent validEvent() {
        return eventWithId("evt-00132");
    }

    private static SecurityEvent eventWithId(String id) {
        return new SecurityEvent(
                id,
                Instant.parse("2026-05-20T14:32:10Z"),
                14227L,
                "pol_web1",
                "203.0.113.42",
                "www.example.com",
                "/api/v1/login",
                "POST",
                403,
                "Mozilla/5.0",
                new Rule("950001", "SQL_INJECTION", "SQLi", Severity.CRITICAL, RuleCategory.INJECTION),
                Action.DENY,
                new GeoLocation("CN", "Beijing"),
                1024L,
                256L);
    }
}
