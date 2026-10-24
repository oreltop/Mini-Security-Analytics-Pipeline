package com.akamai.miniwsa.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.EnrichedEvent;
import com.akamai.miniwsa.domain.GeoLocation;
import com.akamai.miniwsa.domain.Rule;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.domain.SecurityEvent;
import com.akamai.miniwsa.domain.Severity;
import com.akamai.miniwsa.stats.AttackerStats;
import com.akamai.miniwsa.stats.CategoryStats;
import com.akamai.miniwsa.stats.PathCount;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    @Test
    void countsEventsByConfigWithinTimestampRange() {
        Instant base = Instant.parse("2026-05-20T12:00:00Z");
        repository.save(enrichedWith("a", 1L, base));                          // config 1, in range
        repository.save(enrichedWith("b", 1L, base.plus(Duration.ofHours(1))));// config 1, in range
        repository.save(enrichedWith("c", 2L, base));                          // config 2, in range
        repository.save(enrichedWith("d", 1L, base.minus(Duration.ofDays(2))));// config 1, out of range

        Instant from = Instant.parse("2026-05-20T00:00:00Z");
        Instant to = Instant.parse("2026-05-21T00:00:00Z");

        assertThat(repository.countInRange(1L, from, to)).isEqualTo(2);
        assertThat(repository.countInRange(null, from, to)).isEqualTo(3); // all configs
    }

    @Test
    void countMatchingTreatsFromAndToAsOptional() {
        Instant early = Instant.parse("2026-05-19T12:00:00Z");
        Instant mid = Instant.parse("2026-05-20T12:00:00Z");
        Instant late = Instant.parse("2026-05-21T12:00:00Z");
        repository.save(enrichedWith("early", 1L, early));
        repository.save(enrichedWith("mid", 1L, mid));
        repository.save(enrichedWith("late", 1L, late));

        Instant dayStart = Instant.parse("2026-05-20T00:00:00Z");
        Instant dayEnd = Instant.parse("2026-05-20T23:59:59Z");

        assertThat(repository.countMatching(1L, dayStart, dayEnd, null, null)).isEqualTo(1);
        assertThat(repository.countMatching(1L, dayStart, null, null, null)).isEqualTo(2);
        assertThat(repository.countMatching(1L, null, dayEnd, null, null)).isEqualTo(2);
        assertThat(repository.countMatching(1L, null, null, null, null)).isEqualTo(3);
    }

    @Test
    void findPageReturnsNewestFirstWithLimitAndOffset() {
        Instant t1 = Instant.parse("2026-05-20T10:00:00Z");
        Instant t2 = Instant.parse("2026-05-20T11:00:00Z");
        Instant t3 = Instant.parse("2026-05-20T12:00:00Z");
        repository.save(enrichedWith("old", 1L, t1));
        repository.save(enrichedWith("mid", 1L, t2));
        repository.save(enrichedWith("new", 1L, t3));

        var page = repository.findPage(1L, null, null, null, null, 2, 0);
        assertThat(page).extracting(e -> e.event().eventId()).containsExactly("new", "mid");

        var secondPage = repository.findPage(1L, null, null, null, null, 2, 2);
        assertThat(secondPage).extracting(e -> e.event().eventId()).containsExactly("old");
    }

    @Test
    void aggregatesStatsByCategory() {
        Instant base = Instant.parse("2026-05-20T12:00:00Z");
        repository.save(enrichedCat("a", 1L, base, RuleCategory.INJECTION, 80));
        repository.save(enrichedCat("b", 1L, base, RuleCategory.INJECTION, 60));
        repository.save(enrichedCat("c", 1L, base, RuleCategory.BOT, 40));

        Instant from = Instant.parse("2026-05-20T00:00:00Z");
        Instant to = Instant.parse("2026-05-21T00:00:00Z");
        Map<RuleCategory, CategoryStats> byCategory = repository.statsByCategory(1L, from, to);

        assertThat(byCategory).hasSize(2);
        assertThat(byCategory.get(RuleCategory.INJECTION).count()).isEqualTo(2);
        assertThat(byCategory.get(RuleCategory.INJECTION).avgThreatScore()).isEqualTo(70.0);
        assertThat(byCategory.get(RuleCategory.BOT).count()).isEqualTo(1);
        assertThat(byCategory.get(RuleCategory.BOT).avgThreatScore()).isEqualTo(40.0);
    }

    @Test
    void countsEventsByAction() {
        Instant base = Instant.parse("2026-05-20T12:00:00Z");
        repository.save(enrichedAction("a", 1L, base, Action.DENY));
        repository.save(enrichedAction("b", 1L, base, Action.DENY));
        repository.save(enrichedAction("c", 1L, base, Action.ALERT));

        Instant from = Instant.parse("2026-05-20T00:00:00Z");
        Instant to = Instant.parse("2026-05-21T00:00:00Z");
        Map<Action, Long> byAction = repository.countByAction(1L, from, to);

        assertThat(byAction).hasSize(2);
        assertThat(byAction.get(Action.DENY)).isEqualTo(2L);
        assertThat(byAction.get(Action.ALERT)).isEqualTo(1L);
    }

    @Test
    void ranksTopAttackersByEventCount() {
        Instant base = Instant.parse("2026-05-20T12:00:00Z");
        repository.save(enrichedIp("a1", 1L, base, "1.1.1.1", 80));
        repository.save(enrichedIp("a2", 1L, base, "1.1.1.1", 60));
        repository.save(enrichedIp("a3", 1L, base, "1.1.1.1", 40));
        repository.save(enrichedIp("b1", 1L, base, "2.2.2.2", 50));
        repository.save(enrichedIp("b2", 1L, base, "2.2.2.2", 70));
        repository.save(enrichedIp("c1", 1L, base, "3.3.3.3", 90));

        Instant from = Instant.parse("2026-05-20T00:00:00Z");
        Instant to = Instant.parse("2026-05-21T00:00:00Z");
        List<AttackerStats> top = repository.topAttackers(1L, from, to, 10);

        assertThat(top).hasSize(3);
        assertThat(top.get(0).clientIp()).isEqualTo("1.1.1.1");
        assertThat(top.get(0).count()).isEqualTo(3);
        assertThat(top.get(0).avgThreatScore()).isEqualTo(60.0);
        assertThat(top.get(1).clientIp()).isEqualTo("2.2.2.2");
        assertThat(top.get(1).count()).isEqualTo(2);
        assertThat(top.get(1).avgThreatScore()).isEqualTo(60.0);
        assertThat(top.get(2).clientIp()).isEqualTo("3.3.3.3");
        assertThat(top.get(2).count()).isEqualTo(1);
    }

    @Test
    void ranksTopTargetedPathsByEventCount() {
        Instant base = Instant.parse("2026-05-20T12:00:00Z");
        repository.save(enrichedPath("a1", 1L, base, "/login"));
        repository.save(enrichedPath("a2", 1L, base, "/login"));
        repository.save(enrichedPath("a3", 1L, base, "/login"));
        repository.save(enrichedPath("b1", 1L, base, "/admin"));
        repository.save(enrichedPath("b2", 1L, base, "/admin"));
        repository.save(enrichedPath("c1", 1L, base, "/api"));

        Instant from = Instant.parse("2026-05-20T00:00:00Z");
        Instant to = Instant.parse("2026-05-21T00:00:00Z");
        List<PathCount> top = repository.topPaths(1L, from, to, 10);

        assertThat(top).hasSize(3);
        assertThat(top.get(0).path()).isEqualTo("/login");
        assertThat(top.get(0).count()).isEqualTo(3);
        assertThat(top.get(1).path()).isEqualTo("/admin");
        assertThat(top.get(1).count()).isEqualTo(2);
        assertThat(top.get(2).path()).isEqualTo("/api");
        assertThat(top.get(2).count()).isEqualTo(1);
    }

    private static EnrichedEvent enrichedCat(String id, long configId, Instant timestamp,
            RuleCategory category, int threatScore) {
        SecurityEvent event = new SecurityEvent(
                id, timestamp, configId, "pol_web1", "203.0.113.42", "www.example.com",
                "/api/v1/login", "POST", 403, "ua",
                new Rule("950001", "RULE", "msg", Severity.CRITICAL, category),
                Action.DENY, new GeoLocation("CN", "Beijing"), 1024L, 256L);
        return new EnrichedEvent(event, category.attackType(), threatScore, timestamp);
    }

    private static EnrichedEvent enrichedAction(String id, long configId, Instant timestamp, Action action) {
        SecurityEvent event = new SecurityEvent(
                id, timestamp, configId, "pol_web1", "203.0.113.42", "www.example.com",
                "/api/v1/login", "POST", 403, "ua",
                new Rule("950001", "RULE", "msg", Severity.CRITICAL, RuleCategory.INJECTION),
                action, new GeoLocation("CN", "Beijing"), 1024L, 256L);
        return new EnrichedEvent(event, "SQL/Command Injection", 75, timestamp);
    }

    private static EnrichedEvent enrichedIp(String id, long configId, Instant timestamp,
            String clientIp, int threatScore) {
        SecurityEvent event = new SecurityEvent(
                id, timestamp, configId, "pol_web1", clientIp, "www.example.com",
                "/api/v1/login", "POST", 403, "ua",
                new Rule("950001", "RULE", "msg", Severity.CRITICAL, RuleCategory.INJECTION),
                Action.DENY, new GeoLocation("CN", "Beijing"), 1024L, 256L);
        return new EnrichedEvent(event, "SQL/Command Injection", threatScore, timestamp);
    }

    private static EnrichedEvent enrichedPath(String id, long configId, Instant timestamp, String path) {
        SecurityEvent event = new SecurityEvent(
                id, timestamp, configId, "pol_web1", "203.0.113.42", "www.example.com",
                path, "POST", 403, "ua",
                new Rule("950001", "RULE", "msg", Severity.CRITICAL, RuleCategory.INJECTION),
                Action.DENY, new GeoLocation("CN", "Beijing"), 1024L, 256L);
        return new EnrichedEvent(event, "SQL/Command Injection", 75, timestamp);
    }

    private static EnrichedEvent enrichedWith(String id, long configId, Instant timestamp) {
        SecurityEvent event = new SecurityEvent(
                id, timestamp, configId, "pol_web1", "203.0.113.42", "www.example.com",
                "/api/v1/login", "POST", 403, "ua",
                new Rule("950001", "SQL_INJECTION", "msg", Severity.CRITICAL, RuleCategory.INJECTION),
                Action.DENY, new GeoLocation("CN", "Beijing"), 1024L, 256L);
        return new EnrichedEvent(event, "SQL/Command Injection", 75, timestamp);
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
