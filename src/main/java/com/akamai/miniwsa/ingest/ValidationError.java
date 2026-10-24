package com.akamai.miniwsa.ingest;

/** A single field-level validation failure, suitable for a 400 error body. */
public record ValidationError(
        String field,
        String message
) {
}
