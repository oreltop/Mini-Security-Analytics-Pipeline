package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.domain.EnrichedEvent;
import com.akamai.miniwsa.domain.SecurityEvent;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transport adapter for event ingestion. Deliberately thin: it deserializes the
 * request and delegates to {@link EventIngestionService#ingest(List)}, which owns
 * all validation and enrichment. Rejections surface as InvalidEventException and
 * are translated to a 400 by {@link IngestionExceptionHandler}.
 *
 * <p>Accepts a single event object or an array; both bind as a list (see
 * accept-single-value-as-array), and the response is always the list of enriched
 * events that were stored.
 */
@RestController
@RequestMapping("/v1/events")
public class EventIngestionController {

    private final EventIngestionService ingestionService;

    public EventIngestionController(EventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<List<EnrichedEvent>> ingest(@RequestBody List<SecurityEvent> events) {
        List<EnrichedEvent> accepted = ingestionService.ingest(events);
        return ResponseEntity.status(HttpStatus.CREATED).body(accepted);
    }
}
