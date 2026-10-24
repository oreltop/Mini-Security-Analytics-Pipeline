package com.akamai.miniwsa.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for the ClickHouse repository against a real ClickHouse
 * (Testcontainers). Tagged "integration" so it is excluded from the default
 * `mvn test`; run with `-Pintegration`. Exercises the same contract as the
 * in-memory repository.
 */
@Tag("integration")
@Testcontainers
class ClickHouseEventRepositoryTest {

    // The clickhouse-server image gives the "default" user a password by default, so we
    // set a known one (passed to the image as CLICKHOUSE_PASSWORD) rather than rely on empty.
    @Container
    static final ClickHouseContainer CLICKHOUSE =
            new ClickHouseContainer(DockerImageName.parse("clickhouse/clickhouse-server:24.3"))
                    .withUsername("default")
                    .withPassword("test");

    private static JdbcTemplate jdbcTemplate;

    private ClickHouseEventRepository repository;

    @BeforeAll
    static void startAndMigrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(CLICKHOUSE.getJdbcUrl());
        dataSource.setDriverClassName(CLICKHOUSE.getDriverClassName());
        dataSource.setUsername(CLICKHOUSE.getUsername());
        // Only set a password when there is one; an empty-string password is sent as a
        // (wrong) credential that the no-password "default" user rejects.
        if (!CLICKHOUSE.getPassword().isEmpty()) {
            dataSource.setPassword(CLICKHOUSE.getPassword());
        }
        jdbcTemplate = new JdbcTemplate(dataSource);

        String ddl = new String(
                ClickHouseEventRepositoryTest.class.getResourceAsStream("/clickhouse-schema.sql").readAllBytes(),
                UTF_8);
        jdbcTemplate.execute(ddl);
    }

    @BeforeEach
    void freshTable() {
        jdbcTemplate.execute("TRUNCATE TABLE events");
        repository = new ClickHouseEventRepository(jdbcTemplate);
    }

    @Test
    void savedEventCanBeReadBack() {
        EnrichedEvent event = sampleEnriched("evt-1", "1.1.1.1", Instant.now().truncatedTo(ChronoUnit.MILLIS));

        repository.save(event);

        assertThat(repository.findAll()).hasSize(1);
        EnrichedEvent stored = repository.findAll().get(0);
        assertThat(stored.event().eventId()).isEqualTo("evt-1");
        assertThat(stored.event().clientIp()).isEqualTo("1.1.1.1");
        assertThat(stored.attackType()).isEqualTo("SQL/Command Injection");
        assertThat(stored.threatScore()).isEqualTo(75);
    }

    @Test
    void countsOnlyMatchingIpWithinWindow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repository.save(sampleEnriched("a", "1.1.1.1", now));
        repository.save(sampleEnriched("b", "1.1.1.1", now.minus(Duration.ofMinutes(5))));
        repository.save(sampleEnriched("c", "2.2.2.2", now));
        repository.save(sampleEnriched("d", "1.1.1.1", now.minus(Duration.ofMinutes(20))));

        long count = repository.countByClientIpSince("1.1.1.1", now.minus(Duration.ofMinutes(10)));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countsEventsByConfigWithinTimestampRange() {
        Instant base = Instant.parse("2026-05-20T12:00:00Z");
        repository.save(enrichedWith("a", 1L, base));
        repository.save(enrichedWith("b", 1L, base.plus(Duration.ofHours(1))));
        repository.save(enrichedWith("c", 2L, base));
        repository.save(enrichedWith("d", 1L, base.minus(Duration.ofDays(2))));

        Instant from = Instant.parse("2026-05-20T00:00:00Z");
        Instant to = Instant.parse("2026-05-21T00:00:00Z");

        assertThat(repository.countInRange(1L, from, to)).isEqualTo(2);
        assertThat(repository.countInRange(null, from, to)).isEqualTo(3);
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

    private static EnrichedEvent sampleEnriched(String id, String clientIp, Instant receivedAt) {
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
