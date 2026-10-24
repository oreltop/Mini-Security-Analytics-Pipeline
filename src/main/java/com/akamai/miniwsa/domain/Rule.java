package com.akamai.miniwsa.domain;

/** The security rule that matched the request. */
public record Rule(
        String id,
        String name,
        String message,
        Severity severity,
        RuleCategory category
) {
}
