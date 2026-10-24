package com.akamai.miniwsa.samples;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.RuleCategory;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Samples API. All query params are optional; {@code from}/{@code to} filter on
 * event timestamp when present. Bad timestamps yield 400 via the shared handler.
 */
@RestController
@RequestMapping("/v1/events")
public class SamplesController {

    private final SamplesService samplesService;

    public SamplesController(SamplesService samplesService) {
        this.samplesService = samplesService;
    }

    @GetMapping("/samples")
    public SamplesResponse samples(
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) RuleCategory category,
            @RequestParam(required = false) Action action,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return samplesService.findSamples(
                configId,
                parseOptionalInstant(from),
                parseOptionalInstant(to),
                category,
                action,
                limit,
                offset);
    }

    private static Instant parseOptionalInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
