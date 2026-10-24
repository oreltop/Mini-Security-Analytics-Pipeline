package com.akamai.miniwsa.stats;

import com.akamai.miniwsa.domain.Action;
import com.akamai.miniwsa.domain.RuleCategory;
import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics for a configuration and time range.
 * {@code configId} is null when aggregating across all configurations.
 */
public record StatsSummary(
        Long configId,
        TimeRange timeRange,
        long totalEvents,
        Map<RuleCategory, CategoryStats> byCategory,
        Map<Action, Long> byAction,
        List<AttackerStats> topAttackers,
        List<PathCount> topTargetedPaths
) {
}
