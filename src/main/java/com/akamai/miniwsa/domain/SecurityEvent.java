package com.akamai.miniwsa.domain;

import java.time.Instant;

/**
 * A Security Event (DLR) as received on the wire. Immutable; nested data is
 * modeled with records and the closed value sets with enums, so deserialization
 * itself enforces much of the schema. Enrichment fields (attackType,
 * threatScore, receivedAt) are intentionally absent here and will be added when
 * a test forces them.
 */
public record SecurityEvent(
        String eventId,
        Instant timestamp,
        long configId,
        String policyId,
        String clientIp,
        String hostname,
        String path,
        String method,
        int statusCode,
        String userAgent,
        Rule rule,
        Action action,
        GeoLocation geoLocation,
        long requestSize,
        long responseSize
) {
}
