package com.akamai.miniwsa.ingest;

import java.util.List;

/**
 * Thrown by the ingestion core when an event fails validation. Carries the
 * field-level errors so the transport layer can render them into a 400 body.
 */
public class InvalidEventException extends RuntimeException {

    private final transient List<ValidationError> errors;

    public InvalidEventException(List<ValidationError> errors) {
        super("Event failed validation with " + errors.size() + " error(s)");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> errors() {
        return errors;
    }
}
