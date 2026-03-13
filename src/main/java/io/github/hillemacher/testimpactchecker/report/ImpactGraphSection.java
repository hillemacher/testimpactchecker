package io.github.hillemacher.testimpactchecker.report;

import lombok.NonNull;

/**
 * Focused graph view for a single changed-class cause.
 *
 * @param cause cause label represented by this section
 * @param impactedTypeCount number of impacted type nodes in the focused graph
 * @param impactedTestCount number of impacted test nodes in the focused graph
 * @param graph focused per-cause graph
 */
public record ImpactGraphSection(
    @NonNull String cause,
    int impactedTypeCount,
    int impactedTestCount,
    @NonNull ImpactGraph graph) {

  /**
   * Validates section counters and graph fields.
   *
   * @param cause cause label represented by this section
   * @param impactedTypeCount number of impacted type nodes in the focused graph
   * @param impactedTestCount number of impacted test nodes in the focused graph
   * @param graph focused per-cause graph
   * @throws NullPointerException if any required reference parameter is {@code null}
   * @throws IllegalArgumentException if any count is negative
   */
  public ImpactGraphSection {
    if (impactedTypeCount < 0) {
      throw new IllegalArgumentException("impactedTypeCount must be >= 0");
    }
    if (impactedTestCount < 0) {
      throw new IllegalArgumentException("impactedTestCount must be >= 0");
    }
  }
}
