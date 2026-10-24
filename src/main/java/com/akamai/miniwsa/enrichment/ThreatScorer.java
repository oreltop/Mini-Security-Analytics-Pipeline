package com.akamai.miniwsa.enrichment;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.SecurityEvent;
import com.akamai.miniwsa.domain.Severity;
import org.springframework.stereotype.Component;

/**
 * Computes an event's threat score (Part 2.2). The whole scoring policy lives
 * here so it reads as one formula. The repeat-offender bonus (+15 for more than
 * 5 events from the same IP within 10 minutes) needs stored history and will be
 * added when storage is wired into the pipeline.
 */
@Component
public class ThreatScorer {

    private static final int MAX_SCORE = 100;
    private static final int PATH_BONUS = 15;

    public int score(SecurityEvent event) {
        int score = severityWeight(event.rule().severity())
                + actionWeight(event.action())
                + pathBonus(event.path());
        // Spec says cap at 100. With current weights the max is 90, so this is a
        // defensive guard that keeps the contract explicit if weights change.
        return Math.min(score, MAX_SCORE);
    }

    private int severityWeight(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 40;
            case HIGH -> 30;
            case MEDIUM -> 20;
            case LOW -> 10;
        };
    }

    private int actionWeight(Action action) {
        return switch (action) {
            case DENY -> 20;
            case ALERT -> 10;
            case MONITOR -> 0;
        };
    }

    private int pathBonus(String path) {
        if (path == null) {
            return 0;
        }
        return (path.contains("/admin") || path.contains("/login")) ? PATH_BONUS : 0;
    }
}
