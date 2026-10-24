package com.akamai.miniwsa.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.GeoLocation;
import com.akamai.miniwsa.domain.Rule;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.domain.SecurityEvent;
import com.akamai.miniwsa.domain.Severity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The in-memory store is the first EventRepository implementation. These tests
 * pin its most basic contract: nothing is there until you save, and a saved
 * event can be read back. Richer queries will be driven by the stats/samples
 * tests that actually need them.
 */
class InMemoryEventRepositoryTest {

    private final InMemoryEventRepository repository = new InMemoryEventRepository();

    @Test
    void freshRepositoryIsEmpty() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void savedEventCanBeReadBack() {
        SecurityEvent event = sampleEvent("evt-1");

        repository.save(event);

        assertThat(repository.findAll()).containsExactly(event);
    }

    private static SecurityEvent sampleEvent(String id) {
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
                "ua",
                new Rule("950001", "SQL_INJECTION", "msg", Severity.CRITICAL, RuleCategory.INJECTION),
                Action.DENY,
                new GeoLocation("CN", "Beijing"),
                1024L,
                256L);
    }
}
