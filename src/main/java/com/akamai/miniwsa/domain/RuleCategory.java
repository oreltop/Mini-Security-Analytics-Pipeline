package com.akamai.miniwsa.domain;

/**
 * The closed set of rule categories a security event can carry.
 * Modeling this as an enum means an unknown value (e.g. "BANANA") fails to
 * deserialize, which is exactly the Part 1 schema validation we want.
 */
public enum RuleCategory {
    INJECTION("SQL/Command Injection"),
    XSS("Cross-Site Scripting"),
    PROTOCOL_VIOLATION("Protocol Anomaly"),
    DATA_LEAKAGE("Data Exfiltration"),
    BOT("Bot Activity"),
    DOS("Denial of Service"),
    RATE_LIMIT("Rate Limiting");

    private final String attackType;

    RuleCategory(String attackType) {
        this.attackType = attackType;
    }

    /** Human-readable attack type this category classifies as (Part 2.1). */
    public String attackType() {
        return attackType;
    }
}
