package com.akamai.miniwsa.stats;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.storage.EventRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Assembles the stats summary from the repository's aggregation queries. The
 * aggregation itself runs in the store (SQL in ClickHouse, streams in-memory);
 * this service just orchestrates and shapes the response.
 */
@Service
public class StatsService {

    private final EventRepository repository;

    public StatsService(EventRepository repository) {
        this.repository = repository;
    }

    public StatsSummary summarize(Long configId, Instant from, Instant to) {
        long totalEvents = repository.countInRange(configId, from, to);
        Map<RuleCategory, CategoryStats> byCategory = repository.statsByCategory(configId, from, to);
        Map<Action, Long> byAction = repository.countByAction(configId, from, to);
        return new StatsSummary(configId, new TimeRange(from, to), totalEvents, byCategory, byAction);
    }
}
