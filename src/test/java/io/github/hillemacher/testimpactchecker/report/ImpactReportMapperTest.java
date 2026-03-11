package io.github.hillemacher.testimpactchecker.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests deterministic mapping from raw impact data to the immutable HTML report model.
 */
class ImpactReportMapperTest {

  /**
   * Verifies sorting and aggregate metrics are stable for a non-empty impact set.
   */
  @Test
  void testToImpactReportBuildsDeterministicSortedModel() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper = new ImpactReportMapper(
        Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));

    final Map<Path, Set<String>> relevantTestsWithCauses = Map.of(
        projectPath.resolve("module/src/test/java/b/TestB.java"), Set.of("BClass", "AClass"),
        projectPath.resolve("module/src/test/java/a/TestA.java"), Set.of("AClass"));

    final ImpactReport report = mapper.toImpactReport(projectPath, relevantTestsWithCauses);

    assertThat(report.generatedAt()).isEqualTo(Instant.parse("2026-03-11T09:15:00Z"));
    assertThat(report.projectPath()).isEqualTo(projectPath);
    assertThat(report.impactedTestsCount()).isEqualTo(2);
    assertThat(report.uniqueCausesCount()).isEqualTo(2);
    assertThat(report.averageCausesPerTest()).isEqualTo(1.5);

    assertThat(report.impactedTests()).extracting(entry -> entry.relativeTestPath().toString())
        .containsExactly("module/src/test/java/a/TestA.java", "module/src/test/java/b/TestB.java");
    assertThat(report.impactedTests().get(1).causes()).containsExactly("AClass", "BClass");

    assertThat(report.topCauses()).containsExactly(
        new CauseSummaryEntry("AClass", 2),
        new CauseSummaryEntry("BClass", 1));
  }

  /**
   * Verifies mapper output is well-formed when no impacted tests are present.
   */
  @Test
  void testToImpactReportHandlesEmptyResult() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper = new ImpactReportMapper(
        Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));

    final ImpactReport report = mapper.toImpactReport(projectPath, Map.of());

    assertThat(report.impactedTestsCount()).isZero();
    assertThat(report.uniqueCausesCount()).isZero();
    assertThat(report.averageCausesPerTest()).isZero();
    assertThat(report.impactedTests()).isEmpty();
    assertThat(report.topCauses()).isEmpty();
  }
}
