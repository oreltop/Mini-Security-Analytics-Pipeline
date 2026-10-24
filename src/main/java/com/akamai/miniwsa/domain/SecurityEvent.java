package com.akamai.miniwsa.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * A Security Event (DLR) as received on the wire. Immutable; nested data is
 * modeled with records and the closed value sets with enums, so deserialization
 * itself enforces much of the schema. The validation constraints enforce the
 * "required fields" half of the Part 1 schema check; they are applied by the
 * ingestion core, not the transport layer. Enrichment fields (attackType,
 * threatScore, receivedAt) are intentionally absent here and will be added when
 * a test forces them.
 */
public record SecurityEvent(
        @NotBlank String eventId,
        @NotNull Instant timestamp,
        long configId,
        String policyId,
        @NotBlank String clientIp,
        @NotBlank String hostname,
        @NotBlank String path,
        @NotBlank String method,
        int statusCode,
        String userAgent,
        @NotNull @Valid Rule rule,
        @NotNull Action action,
        @Valid GeoLocation geoLocation,
        long requestSize,
        long responseSize
) {
}
