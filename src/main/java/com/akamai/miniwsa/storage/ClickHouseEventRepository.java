package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.EnrichedEvent;
import com.akamai.miniwsa.domain.GeoLocation;
import com.akamai.miniwsa.domain.Rule;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.domain.SecurityEvent;
import com.akamai.miniwsa.domain.Severity;
import com.akamai.miniwsa.stats.CategoryStats;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * ClickHouse-backed {@link EventRepository}, active under the "clickhouse" profile.
 * Columnar OLAP store suited to the analytical, append-heavy workload (see
 * docs/storage.md). Stores the flattened enriched event; the DateTime64 columns
 * are UTC, so Instants are converted via UTC LocalDateTime.
 */
@Repository
@Profile("clickhouse")
public class ClickHouseEventRepository implements EventRepository {

    private static final String INSERT = """
            INSERT INTO events (
                eventId, timestamp, configId, policyId, clientIp, hostname, path, method,
                statusCode, userAgent, ruleId, ruleName, ruleMessage, severity, category,
                action, country, city, requestSize, responseSize, attackType, threatScore, receivedAt
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private static final String SELECT_ALL = "SELECT * FROM events";

    private static final String COUNT_BY_IP_SINCE =
            "SELECT count() FROM events WHERE clientIp = ? AND receivedAt >= ?";

    private final JdbcTemplate jdbcTemplate;

    public ClickHouseEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(EnrichedEvent enriched) {
        SecurityEvent e = enriched.event();
        Rule rule = e.rule();
        GeoLocation geo = e.geoLocation();
        jdbcTemplate.update(INSERT,
                e.eventId(),
                utc(e.timestamp()),
                e.configId(),
                orEmpty(e.policyId()),
                e.clientIp(),
                e.hostname(),
                e.path(),
                e.method(),
                e.statusCode(),
                orEmpty(e.userAgent()),
                rule.id(),
                rule.name(),
                orEmpty(rule.message()),
                rule.severity().name(),
                rule.category().name(),
                e.action().name(),
                geo != null ? orEmpty(geo.country()) : "",
                geo != null ? orEmpty(geo.city()) : "",
                e.requestSize(),
                e.responseSize(),
                enriched.attackType(),
                enriched.threatScore(),
                utc(enriched.receivedAt()));
    }

    @Override
    public List<EnrichedEvent> findAll() {
        return jdbcTemplate.query(SELECT_ALL, enrichedEventRowMapper());
    }

    @Override
    public long countByClientIpSince(String clientIp, Instant since) {
        Number count = jdbcTemplate.queryForObject(
                COUNT_BY_IP_SINCE, Number.class, clientIp, utc(since));
        return count == null ? 0L : count.longValue();
    }

    @Override
    public long countInRange(Long configId, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder(
                "SELECT count() FROM events WHERE timestamp >= ? AND timestamp <= ?");
        List<Object> args = new ArrayList<>();
        args.add(utc(from));
        args.add(utc(to));
        if (configId != null) {
            sql.append(" AND configId = ?");
            args.add(configId);
        }
        Number count = jdbcTemplate.queryForObject(sql.toString(), Number.class, args.toArray());
        return count == null ? 0L : count.longValue();
    }

    @Override
    public Map<RuleCategory, CategoryStats> statsByCategory(Long configId, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder(
                "SELECT category, count() AS cnt, avg(threatScore) AS avg FROM events "
                        + "WHERE timestamp >= ? AND timestamp <= ?");
        List<Object> args = new ArrayList<>();
        args.add(utc(from));
        args.add(utc(to));
        if (configId != null) {
            sql.append(" AND configId = ?");
            args.add(configId);
        }
        sql.append(" GROUP BY category");

        Map<RuleCategory, CategoryStats> byCategory = new HashMap<>();
        RowCallbackHandler handler = rs -> byCategory.put(
                RuleCategory.valueOf(rs.getString("category")),
                new CategoryStats(rs.getLong("cnt"), rs.getDouble("avg")));
        jdbcTemplate.query(sql.toString(), handler, args.toArray());
        return byCategory;
    }

    private RowMapper<EnrichedEvent> enrichedEventRowMapper() {
        return (rs, rowNum) -> {
            SecurityEvent event = new SecurityEvent(
                    rs.getString("eventId"),
                    rs.getObject("timestamp", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rs.getLong("configId"),
                    rs.getString("policyId"),
                    rs.getString("clientIp"),
                    rs.getString("hostname"),
                    rs.getString("path"),
                    rs.getString("method"),
                    rs.getInt("statusCode"),
                    rs.getString("userAgent"),
                    new Rule(
                            rs.getString("ruleId"),
                            rs.getString("ruleName"),
                            rs.getString("ruleMessage"),
                            Severity.valueOf(rs.getString("severity")),
                            RuleCategory.valueOf(rs.getString("category"))),
                    Action.valueOf(rs.getString("action")),
                    new GeoLocation(rs.getString("country"), rs.getString("city")),
                    rs.getLong("requestSize"),
                    rs.getLong("responseSize"));
            return new EnrichedEvent(
                    event,
                    rs.getString("attackType"),
                    rs.getInt("threatScore"),
                    rs.getObject("receivedAt", LocalDateTime.class).toInstant(ZoneOffset.UTC));
        };
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
