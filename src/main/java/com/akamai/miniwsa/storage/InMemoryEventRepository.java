package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.domain.SecurityEvent;
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

    private final Map<String, SecurityEvent> eventsById = new ConcurrentHashMap<>();

    @Override
    public void save(SecurityEvent event) {
        eventsById.put(event.eventId(), event);
    }

    @Override
    public List<SecurityEvent> findAll() {
        return new ArrayList<>(eventsById.values());
    }
}
