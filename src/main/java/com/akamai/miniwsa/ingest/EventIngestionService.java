package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.domain.EnrichedEvent;
import com.akamai.miniwsa.domain.SecurityEvent;
import com.akamai.miniwsa.enrichment.ThreatScorer;
import com.akamai.miniwsa.storage.EventRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The core ingestion operation. Every path into the system (REST, the data
 * generator, a future queue consumer) goes through {@link #ingest}, so this is
 * where untrusted input is validated, enriched, and stored — never the transport
 * layer alone.
 */
@Service
public class EventIngestionService {

    private static final Duration REPEAT_OFFENDER_WINDOW = Duration.ofMinutes(10);

    private final Validator validator;
    private final ThreatScorer threatScorer;
    private final EventRepository repository;

    public EventIngestionService(Validator validator, ThreatScorer threatScorer,
            EventRepository repository) {
        this.validator = validator;
        this.threatScorer = threatScorer;
        this.repository = repository;
    }

    public EnrichedEvent ingest(SecurityEvent event) {
        validate(event);
        EnrichedEvent enriched = enrich(event);
        repository.save(enriched);
        return enriched;
    }

    private void validate(SecurityEvent event) {
        Set<ConstraintViolation<SecurityEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            List<ValidationError> errors = violations.stream()
                    .map(v -> new ValidationError(v.getPropertyPath().toString(), v.getMessage()))
                    .sorted(Comparator.comparing(ValidationError::field))
                    .toList();
            throw new InvalidEventException(errors);
        }
    }

    private EnrichedEvent enrich(SecurityEvent event) {
        Instant receivedAt = Instant.now();
        // Count prior events from this IP in the window (this event isn't stored yet).
        long recentFromSameIp = repository.countByClientIpSince(
                event.clientIp(), receivedAt.minus(REPEAT_OFFENDER_WINDOW));
        String attackType = event.rule().category().attackType();
        int threatScore = threatScorer.score(event, recentFromSameIp);
        return new EnrichedEvent(event, attackType, threatScore, receivedAt);
    }
}
