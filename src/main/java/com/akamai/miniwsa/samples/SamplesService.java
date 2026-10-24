package com.akamai.miniwsa.samples;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.storage.EventRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Assembles the samples envelope from {@code countMatching} + {@code findPage}.
 */
@Service
public class SamplesService {

    private final EventRepository repository;

    public SamplesService(EventRepository repository) {
        this.repository = repository;
    }

    public SamplesResponse findSamples(
            Long configId,
            Instant from,
            Instant to,
            RuleCategory category,
            Action action,
            int limit,
            int offset) {
        long total = repository.countMatching(configId, from, to, category, action);
        var items = repository.findPage(configId, from, to, category, action, limit, offset);
        return new SamplesResponse(total, limit, offset, items);
    }
}
