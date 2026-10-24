package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.EnrichedEvent;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.stats.AttackerStats;
import com.akamai.miniwsa.stats.CategoryStats;
import com.akamai.miniwsa.stats.PathCount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * In-memory {@link EventRepository} backed by a concurrent map keyed by eventId.
 * Thread-safe for concurrent ingestion; keying by eventId also de-duplicates
 * re-sent events. Chosen as the first implementation to unblock all downstream
 * logic quickly — see the README for how this maps onto a real store at scale.
 */
@Repository
@Profile("!clickhouse")
public class InMemoryEventRepository implements EventRepository {

    private final Map<String, EnrichedEvent> eventsById = new ConcurrentHashMap<>();

    @Override
    public void save(EnrichedEvent event) {
        eventsById.put(event.event().eventId(), event);
    }

    @Override
    public List<EnrichedEvent> findAll() {
        return new ArrayList<>(eventsById.values());
    }

    @Override
    public long countByClientIpSince(String clientIp, Instant since) {
        return eventsById.values().stream()
                .filter(e -> e.event().clientIp().equals(clientIp))
                .filter(e -> !e.receivedAt().isBefore(since))
                .count();
    }

    @Override
    public long countInRange(Long configId, Instant from, Instant to) {
        return eventsById.values().stream()
                .filter(e -> configId == null || configId.equals(e.event().configId()))
                .filter(e -> withinRange(e.event().timestamp(), from, to))
                .count();
    }

    @Override
    public Map<RuleCategory, CategoryStats> statsByCategory(Long configId, Instant from, Instant to) {
        return eventsById.values().stream()
                .filter(e -> configId == null || configId.equals(e.event().configId()))
                .filter(e -> withinRange(e.event().timestamp(), from, to))
                .collect(Collectors.groupingBy(
                        e -> e.event().rule().category(),
                        Collectors.collectingAndThen(Collectors.toList(), group -> new CategoryStats(
                                group.size(),
                                group.stream().mapToInt(EnrichedEvent::threatScore).average().orElse(0.0)))));
    }

    @Override
    public Map<Action, Long> countByAction(Long configId, Instant from, Instant to) {
        return eventsById.values().stream()
                .filter(e -> configId == null || configId.equals(e.event().configId()))
                .filter(e -> withinRange(e.event().timestamp(), from, to))
                .collect(Collectors.groupingBy(e -> e.event().action(), Collectors.counting()));
    }

    @Override
    public List<AttackerStats> topAttackers(Long configId, Instant from, Instant to, int limit) {
        return eventsById.values().stream()
                .filter(e -> configId == null || configId.equals(e.event().configId()))
                .filter(e -> withinRange(e.event().timestamp(), from, to))
                .collect(Collectors.groupingBy(e -> e.event().clientIp()))
                .entrySet().stream()
                .map(entry -> new AttackerStats(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToInt(EnrichedEvent::threatScore).average().orElse(0.0)))
                .sorted(Comparator.comparingLong(AttackerStats::count).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<PathCount> topPaths(Long configId, Instant from, Instant to, int limit) {
        return eventsById.values().stream()
                .filter(e -> configId == null || configId.equals(e.event().configId()))
                .filter(e -> withinRange(e.event().timestamp(), from, to))
                .collect(Collectors.groupingBy(e -> e.event().path(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new PathCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(PathCount::count).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public long countMatching(
            Long configId, Instant from, Instant to, RuleCategory category, Action action) {
        return eventsById.values().stream()
                .filter(e -> matches(e, configId, from, to, category, action))
                .count();
    }

    @Override
    public List<EnrichedEvent> findPage(
            Long configId,
            Instant from,
            Instant to,
            RuleCategory category,
            Action action,
            int limit,
            int offset) {
        return eventsById.values().stream()
                .filter(e -> matches(e, configId, from, to, category, action))
                .sorted(Comparator.comparing((EnrichedEvent e) -> e.event().timestamp()).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    private static boolean matches(
            EnrichedEvent enriched,
            Long configId,
            Instant from,
            Instant to,
            RuleCategory category,
            Action action) {
        var event = enriched.event();
        if (configId != null && !configId.equals(event.configId())) {
            return false;
        }
        if (from != null && event.timestamp().isBefore(from)) {
            return false;
        }
        if (to != null && event.timestamp().isAfter(to)) {
            return false;
        }
        if (category != null && category != event.rule().category()) {
            return false;
        }
        if (action != null && action != event.action()) {
            return false;
        }
        return true;
    }

    private static boolean withinRange(Instant timestamp, Instant from, Instant to) {
        return !timestamp.isBefore(from) && !timestamp.isAfter(to);
    }
}
