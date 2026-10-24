package com.akamai.miniwsa.domain;

/**
 * The closed set of rule categories a security event can carry.
 * Modeling this as an enum means an unknown value (e.g. "BANANA") fails to
 * deserialize, which is exactly the Part 1 schema validation we want.
 */
public enum RuleCategory {
    INJECTION,
    XSS,
    PROTOCOL_VIOLATION,
    DATA_LEAKAGE,
    BOT,
    DOS,
    RATE_LIMIT
}
