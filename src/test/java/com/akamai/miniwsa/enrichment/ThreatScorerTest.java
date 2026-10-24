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
 * Threat score (Part 2.2):
 *   severity (CRITICAL=40/HIGH=30/MEDIUM=20/LOW=10)
 *   + action (DENY=+20/ALERT=+10/MONITOR=+0)
 *   + 15 if path contains /admin or /login
 *   + 15 if more than 5 recent events from the same IP (repeat offender)
 * The recent-event count is supplied by the caller (the service queries the
 * store); the scorer stays a pure function of its inputs.
 */
class ThreatScorerTest {

    private final ThreatScorer scorer = new ThreatScorer();

    @Test
    void sumsSeverityActionAndPathBonus() {
        // 40 + 20 + 15 = 75, no repeat-offender
        int score = scorer.score(event(Severity.CRITICAL, Action.DENY, "/api/v1/login"), 0);
        assertThat(score).isEqualTo(75);
    }

    @Test
    void noBonusesForLowMonitorPlainPath() {
        // 10 + 0 + 0 = 10
        int score = scorer.score(event(Severity.LOW, Action.MONITOR, "/api/v1/products"), 0);
        assertThat(score).isEqualTo(10);
    }

    @Test
    void adminPathAlsoEarnsTheBonus() {
        // 20 + 10 + 15 = 45
        int score = scorer.score(event(Severity.MEDIUM, Action.ALERT, "/admin/users"), 0);
        assertThat(score).isEqualTo(45);
    }

    @Test
    void highSeverityWeightedCorrectly() {
        // 30 + 0 + 0 = 30
        int score = scorer.score(event(Severity.HIGH, Action.MONITOR, "/status"), 0);
        assertThat(score).isEqualTo(30);
    }

    @Test
    void repeatOffenderAddsBonusAboveThreshold() {
        // base 75 + 15 repeat-offender (6 > 5) = 90
        int score = scorer.score(event(Severity.CRITICAL, Action.DENY, "/api/v1/login"), 6);
        assertThat(score).isEqualTo(90);
    }

    @Test
    void noRepeatOffenderBonusAtThreshold() {
        // exactly 5 is not "more than 5" → base 75, no bonus
        int score = scorer.score(event(Severity.CRITICAL, Action.DENY, "/api/v1/login"), 5);
        assertThat(score).isEqualTo(75);
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
