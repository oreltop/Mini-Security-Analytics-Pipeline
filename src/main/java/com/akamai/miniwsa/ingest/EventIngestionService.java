package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.domain.SecurityEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The core ingestion operation. Every path into the system (REST, the data
 * generator, a future queue consumer) goes through {@link #ingest}, so this is
 * where untrusted input is validated — never the transport layer alone.
 * Enrichment and storage will be added here as later tests demand them.
 */
@Service
public class EventIngestionService {

    private final Validator validator;

    public EventIngestionService(Validator validator) {
        this.validator = validator;
    }

    public SecurityEvent ingest(SecurityEvent event) {
        Set<ConstraintViolation<SecurityEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            List<ValidationError> errors = violations.stream()
                    .map(v -> new ValidationError(v.getPropertyPath().toString(), v.getMessage()))
                    .sorted(Comparator.comparing(ValidationError::field))
                    .toList();
            throw new InvalidEventException(errors);
        }
        return event;
    }
}
