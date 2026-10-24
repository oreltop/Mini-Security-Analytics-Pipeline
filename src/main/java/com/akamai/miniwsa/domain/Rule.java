package com.akamai.miniwsa.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The security rule that matched the request. */
public record Rule(
        @NotBlank String id,
        @NotBlank String name,
        String message,
        @NotNull Severity severity,
        @NotNull RuleCategory category
) {
}
