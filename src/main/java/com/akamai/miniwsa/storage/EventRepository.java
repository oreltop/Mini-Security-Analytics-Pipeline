package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.domain.EnrichedEvent;
import java.time.Instant;
import java.util.List;

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
}
