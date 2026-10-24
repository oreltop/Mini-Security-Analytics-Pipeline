package com.akamai.miniwsa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Classification (Part 2.1): each rule category maps to its human-readable
 * attackType string. The mapping lives on the enum so every category must carry
 * its label — a new category can't be added without one.
 */
class RuleCategoryTest {

    @Test
    void eachCategoryMapsToItsAttackType() {
        assertThat(RuleCategory.INJECTION.attackType()).isEqualTo("SQL/Command Injection");
        assertThat(RuleCategory.XSS.attackType()).isEqualTo("Cross-Site Scripting");
        assertThat(RuleCategory.PROTOCOL_VIOLATION.attackType()).isEqualTo("Protocol Anomaly");
        assertThat(RuleCategory.DATA_LEAKAGE.attackType()).isEqualTo("Data Exfiltration");
        assertThat(RuleCategory.BOT.attackType()).isEqualTo("Bot Activity");
        assertThat(RuleCategory.DOS.attackType()).isEqualTo("Denial of Service");
        assertThat(RuleCategory.RATE_LIMIT.attackType()).isEqualTo("Rate Limiting");
    }
}
