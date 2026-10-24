package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.domain.EnrichedEvent;
import com.akamai.miniwsa.domain.SecurityEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transport adapter for event ingestion. Deliberately thin: it deserializes the
 * request and delegates to {@link EventIngestionService#ingest}, which owns all
 * validation and (later) enrichment. Rejections surface as InvalidEventException
 * and are translated to a 400 by {@link IngestionExceptionHandler}.
 */
@RestController
@RequestMapping("/v1/events")
public class EventIngestionController {

    private final EventIngestionService ingestionService;

    public EventIngestionController(EventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<EnrichedEvent> ingest(@RequestBody SecurityEvent event) {
        EnrichedEvent accepted = ingestionService.ingest(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(accepted);
    }
}
