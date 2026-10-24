package com.akamai.miniwsa.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.GeoLocation;
import com.akamai.miniwsa.domain.Rule;
import com.akamai.miniwsa.domain.RuleCategory;
import com.akamai.miniwsa.domain.SecurityEvent;
import com.akamai.miniwsa.domain.Severity;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Base threat score (Part 2.2), excluding the repeat-offender bonus (which needs
 * stored history and is added when storage is wired in):
 *   severity (CRITICAL=40/HIGH=30/MEDIUM=20/LOW=10)
 *   + action (DENY=+20/ALERT=+10/MONITOR=+0)
 *   + 15 if path contains /admin or /login
 */
class ThreatScorerTest {

    private final ThreatScorer scorer = new ThreatScorer();

    @Test
    void sumsSeverityActionAndPathBonus() {
        // 40 + 20 + 15 = 75
        int score = scorer.score(event(Severity.CRITICAL, Action.DENY, "/api/v1/login"));
        assertThat(score).isEqualTo(75);
    }

    @Test
    void noBonusesForLowMonitorPlainPath() {
        // 10 + 0 + 0 = 10
        int score = scorer.score(event(Severity.LOW, Action.MONITOR, "/api/v1/products"));
        assertThat(score).isEqualTo(10);
    }

    @Test
    void adminPathAlsoEarnsTheBonus() {
        // 20 + 10 + 15 = 45
        int score = scorer.score(event(Severity.MEDIUM, Action.ALERT, "/admin/users"));
        assertThat(score).isEqualTo(45);
    }

    @Test
    void highSeverityWeightedCorrectly() {
        // 30 + 0 + 0 = 30
        int score = scorer.score(event(Severity.HIGH, Action.MONITOR, "/status"));
        assertThat(score).isEqualTo(30);
    }

    private static SecurityEvent event(Severity severity, Action action, String path) {
        return new SecurityEvent(
                "evt-1",
                Instant.parse("2026-05-20T14:32:10Z"),
                14227L,
                "pol_web1",
                "203.0.113.42",
                "www.example.com",
                path,
                "POST",
                403,
                "ua",
                new Rule("950001", "RULE", "msg", severity, RuleCategory.INJECTION),
                action,
                new GeoLocation("CN", "Beijing"),
                1024L,
                256L);
    }
}
