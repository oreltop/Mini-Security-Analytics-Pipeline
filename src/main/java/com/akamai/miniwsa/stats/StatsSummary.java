package com.akamai.miniwsa.stats;

/**
 * Aggregated statistics for a configuration and time range. Grows section by
 * section (byCategory, byAction, topAttackers, topTargetedPaths) as those cycles
 * land. {@code configId} is null when aggregating across all configurations.
 */
public record StatsSummary(
        Long configId,
        TimeRange timeRange,
        long totalEvents
) {
}
