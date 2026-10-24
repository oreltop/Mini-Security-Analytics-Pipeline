package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.EnrichedEvent;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.stats.AttackerStats;
import com.akamai.miniwsa.stats.CategoryStats;
import com.akamai.miniwsa.stats.PathCount;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The storage seam. The application depends on this interface, not on any
 * concrete engine, so the backing store (in-memory today; a database or search
 * engine at real scale) can be swapped without touching ingestion or analytics.
 * It stores enriched events — the original event plus its derived fields.
 */
public interface EventRepository {

    void save(EnrichedEvent event);

    List<EnrichedEvent> findAll();

    /**
     * Counts stored events from the given client IP whose {@code receivedAt} is
     * at or after {@code since}. Used for the repeat-offender threat bonus.
     */
    long countByClientIpSince(String clientIp, Instant since);

    /**
     * Counts stored events whose event {@code timestamp} falls in [{@code from},
     * {@code to}]. A null {@code configId} counts across all configurations.
     * Backs the stats {@code totalEvents}.
     */
    long countInRange(Long configId, Instant from, Instant to);

    /**
     * Event count and average threat score per rule category, over the config +
     * timestamp range. Backs the stats {@code byCategory} section.
     */
    Map<RuleCategory, CategoryStats> statsByCategory(Long configId, Instant from, Instant to);

    /**
     * Event count per action, over the config + timestamp range. Backs the stats
     * {@code byAction} section.
     */
    Map<Action, Long> countByAction(Long configId, Instant from, Instant to);

    /**
     * Top client IPs by event count (with average threat score), over the config +
     * timestamp range. Backs the stats {@code topAttackers} section.
     */
    List<AttackerStats> topAttackers(Long configId, Instant from, Instant to, int limit);

    /**
     * Top request paths by event count, over the config + timestamp range. Backs
     * the stats {@code topTargetedPaths} section.
     */
    List<PathCount> topPaths(Long configId, Instant from, Instant to, int limit);
}
