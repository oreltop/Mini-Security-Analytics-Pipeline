package com.akamai.miniwsa.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.EnrichedEvent;
import com.akamai.miniwsa.domain.GeoLocation;
import com.akamai.miniwsa.domain.Rule;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.domain.SecurityEvent;
import com.akamai.miniwsa.domain.Severity;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The in-memory store is the first EventRepository implementation. These tests
 * pin its contract: nothing is there until you save, a saved enriched event can
 * be read back, and recent events per client IP can be counted (for the
 * repeat-offender bonus).
 */
class InMemoryEventRepositoryTest {

    private final InMemoryEventRepository repository = new InMemoryEventRepository();

    @Test
    void freshRepositoryIsEmpty() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void savedEventCanBeReadBack() {
        EnrichedEvent event = enriched("evt-1", "203.0.113.42", Instant.parse("2026-05-20T14:32:11Z"));

        repository.save(event);

        assertThat(repository.findAll()).containsExactly(event);
    }

    @Test
    void countsOnlyMatchingIpWithinWindow() {
        Instant now = Instant.parse("2026-05-20T14:32:10Z");
        Instant since = now.minus(Duration.ofMinutes(10));

        repository.save(enriched("a", "1.1.1.1", now));                               // match
        repository.save(enriched("b", "1.1.1.1", now.minus(Duration.ofMinutes(5))));  // match
        repository.save(enriched("c", "2.2.2.2", now));                               // other IP
        repository.save(enriched("d", "1.1.1.1", now.minus(Duration.ofMinutes(20)))); // too old

        assertThat(repository.countByClientIpSince("1.1.1.1", since)).isEqualTo(2);
    }

    private static EnrichedEvent enriched(String id, String clientIp, Instant receivedAt) {
        SecurityEvent event = new SecurityEvent(
                id,
                Instant.parse("2026-05-20T14:32:10Z"),
                14227L,
                "pol_web1",
                clientIp,
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
        return new EnrichedEvent(event, "SQL/Command Injection", 75, receivedAt);
    }
}
