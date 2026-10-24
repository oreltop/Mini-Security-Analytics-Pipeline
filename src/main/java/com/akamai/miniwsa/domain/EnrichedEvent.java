package com.akamai.miniwsa.domain;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;

/**
 * An ingested event after enrichment: the original event plus the derived
 * attackType, threatScore, and the server-assigned receivedAt. This is what the
 * system stores and returns.
 *
 * <p>The original event is held by composition (no field duplication) but
 * {@code @JsonUnwrapped} flattens it in JSON, so the wire shape is "the original
 * fields plus attackType/threatScore/receivedAt" rather than a nested object.
 */
public record EnrichedEvent(
        @JsonUnwrapped SecurityEvent event,
        String attackType,
        int threatScore,
        Instant receivedAt
) {
}
