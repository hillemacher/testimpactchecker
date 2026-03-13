package io.github.hillemacher.testimpactchecker.report;

import lombok.NonNull;

/**
 * One aggregated impact cause and the number of impacted tests it contributes to.
 *
 * @param cause changed class name (or other cause token) included in the summary
 * @param impactedTestCount number of impacted tests associated with the cause
 */
public record CauseSummaryEntry(@NonNull String cause, int impactedTestCount) {

  /**
   * Validates the summary entry values.
   *
   * @param cause changed class name (or other cause token) included in the summary
   * @param impactedTestCount number of impacted tests associated with the cause
   * @throws NullPointerException if {@code cause} is {@code null}
   * @throws IllegalArgumentException if {@code impactedTestCount} is negative
   */
  public CauseSummaryEntry {
    if (impactedTestCount < 0) {
      throw new IllegalArgumentException("impactedTestCount must be >= 0");
    }
  }
}
