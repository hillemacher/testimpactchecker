package io.github.hillemacher.testimpactchecker.report;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import lombok.NonNull;

/**
 * Immutable view model used for rendering a static test impact report.
 *
 * @param generatedAt UTC timestamp when the report model was created
 * @param projectPath normalized absolute project path used for analysis
 * @param impactedTestsCount total number of impacted tests
 * @param uniqueCausesCount number of unique causes across all impacted tests
 * @param averageCausesPerTest average number of causes per impacted test
 * @param impactedTests impacted test entries sorted by relative path
 * @param topCauses cause summary entries sorted by descending impact and cause name
 * @param impactGraph focused and capped graph topology used for SVG rendering
 */
public record ImpactReport(
    @NonNull Instant generatedAt,
    @NonNull Path projectPath,
    int impactedTestsCount,
    int uniqueCausesCount,
    double averageCausesPerTest,
    @NonNull List<ImpactedTestEntry> impactedTests,
    @NonNull List<CauseSummaryEntry> topCauses,
    @NonNull ImpactGraph impactGraph) {

  /**
   * Validates and normalizes report data.
   *
   * @param generatedAt UTC timestamp when the report model was created
   * @param projectPath normalized absolute project path used for analysis
   * @param impactedTestsCount total number of impacted tests
   * @param uniqueCausesCount number of unique causes across all impacted tests
   * @param averageCausesPerTest average number of causes per impacted test
   * @param impactedTests impacted test entries sorted by relative path
   * @param topCauses cause summary entries sorted by descending impact and cause name
   * @param impactGraph focused and capped graph topology used for SVG rendering
   * @throws NullPointerException if required reference fields are {@code null}
   * @throws IllegalArgumentException if numeric counters are negative
   */
  public ImpactReport {
    if (impactedTestsCount < 0) {
      throw new IllegalArgumentException("impactedTestsCount must be >= 0");
    }
    if (uniqueCausesCount < 0) {
      throw new IllegalArgumentException("uniqueCausesCount must be >= 0");
    }
    if (averageCausesPerTest < 0) {
      throw new IllegalArgumentException("averageCausesPerTest must be >= 0");
    }

    impactedTests = List.copyOf(impactedTests);
    topCauses = List.copyOf(topCauses);
  }
}
