package com.akamai.miniwsa.ingest;

import java.util.List;

/** The JSON body returned for a 400 when an event fails validation. */
public record ErrorResponse(
        List<ValidationError> errors
) {
}
