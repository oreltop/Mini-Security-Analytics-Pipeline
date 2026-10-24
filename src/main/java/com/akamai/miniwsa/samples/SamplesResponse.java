package com.akamai.miniwsa.samples;

import com.akamai.miniwsa.domain.EnrichedEvent;
import java.util.List;

/** Paginated samples envelope: matching total plus the current page of items. */
public record SamplesResponse(
        long total,
        int limit,
        int offset,
        List<EnrichedEvent> items
) {
}
