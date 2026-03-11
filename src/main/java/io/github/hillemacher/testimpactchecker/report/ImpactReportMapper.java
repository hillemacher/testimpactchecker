package io.github.hillemacher.testimpactchecker.report;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;

/**
 * Maps raw impact detection output into a deterministic report model.
 */
public class ImpactReportMapper {

  private final Clock clock;

  /**
   * Creates a mapper that timestamps reports using the current UTC clock.
   */
  public ImpactReportMapper() {
    this(Clock.systemUTC());
  }

  ImpactReportMapper(@NonNull final Clock clock) {
    this.clock = clock;
  }

  /**
   * Builds an immutable report model from impacted tests and causes.
   *
   * @param projectPath root path of the scanned project
   * @param relevantTestsWithCauses impacted tests keyed by absolute/normalized test path
   * @return deterministic report model for rendering
   * @throws NullPointerException if any parameter is {@code null}
   */
  public ImpactReport toImpactReport(
      @NonNull final Path projectPath,
      @NonNull final Map<Path, Set<String>> relevantTestsWithCauses) {

    final Path normalizedProjectPath = projectPath.toAbsolutePath().normalize();
    final Instant generatedAt = clock.instant();

    final List<ImpactedTestEntry> impactedTests = relevantTestsWithCauses.entrySet().stream()
        .map(entry -> new ImpactedTestEntry(
            normalizedProjectPath.relativize(entry.getKey().toAbsolutePath().normalize()),
            new ArrayList<>(entry.getValue())))
        .sorted(Comparator.comparing(entry -> entry.relativeTestPath().toString()))
        .toList();

    final Map<String, Integer> causeCounts = new java.util.HashMap<>();
    impactedTests.forEach(entry -> entry.causes().forEach(
        cause -> causeCounts.merge(cause, 1, Integer::sum)));

    final List<CauseSummaryEntry> topCauses = causeCounts.entrySet().stream()
        .map(entry -> new CauseSummaryEntry(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparingInt(CauseSummaryEntry::impactedTestCount)
            .reversed()
            .thenComparing(CauseSummaryEntry::cause))
        .toList();

    final int impactedTestsCount = impactedTests.size();
    final int uniqueCausesCount = causeCounts.size();

    // Average number of non-mocked causes per impacted test.
    final double averageCausesPerTest = impactedTestsCount == 0
        ? 0
        : impactedTests.stream().mapToInt(entry -> entry.causes().size()).average().orElse(0);

    return new ImpactReport(generatedAt, normalizedProjectPath, impactedTestsCount, uniqueCausesCount,
        averageCausesPerTest, impactedTests, topCauses);
  }
}
