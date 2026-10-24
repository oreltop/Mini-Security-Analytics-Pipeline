package com.akamai.miniwsa.samples;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.storage.EventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Assembles the samples envelope. Pagination items land in a later cycle; this
 * cycle wires total via {@code countMatching} and returns an empty page.
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
        return new SamplesResponse(total, limit, offset, List.of());
    }
}
