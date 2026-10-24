package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.domain.EnrichedEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * In-memory {@link EventRepository} backed by a concurrent map keyed by eventId.
 * Thread-safe for concurrent ingestion; keying by eventId also de-duplicates
 * re-sent events. Chosen as the first implementation to unblock all downstream
 * logic quickly — see the README for how this maps onto a real store at scale.
 */
@Repository
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
}
