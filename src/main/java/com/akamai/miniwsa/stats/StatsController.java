package com.akamai.miniwsa.stats;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics API. {@code from}/{@code to} are ISO-8601 instants filtering on the
 * event timestamp; {@code configId} is optional (omitted ⇒ all configurations).
 * A bad timestamp yields a 400 via the exception handler.
 */
@RestController
@RequestMapping("/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/summary")
    public StatsSummary summary(
            @RequestParam(required = false) Long configId,
            @RequestParam String from,
            @RequestParam String to) {
        return statsService.summarize(configId, Instant.parse(from), Instant.parse(to));
    }
}
