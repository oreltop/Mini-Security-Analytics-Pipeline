package com.akamai.miniwsa.ingest;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single field-level problem, suitable for an error body. {@code field} is
 * omitted from JSON when null (e.g. a non-field-specific error like a 500).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationError(
        String field,
        String message
) {
}
