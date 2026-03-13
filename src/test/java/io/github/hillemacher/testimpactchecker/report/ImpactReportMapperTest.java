package io.github.hillemacher.testimpactchecker.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    final Map<String, Set<String>> impactedTypeToCauses = Map.of(
        "AClass", Set.of("AClass"),
        "BClass", Set.of("BClass"),
        "FacadeType", Set.of("AClass", "BClass"));

    final ImpactReport report = mapper.toImpactReport(projectPath, relevantTestsWithCauses,
        impactedTypeToCauses);

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

    assertThat(report.impactGraph().nodes()).isNotEmpty();
    assertThat(report.impactGraph().edges()).isNotEmpty();
    assertThat(report.impactGraph().nodes()).anyMatch(node -> node.kind() == ImpactGraphNodeKind.CAUSE);
    assertThat(report.impactGraph().nodes()).anyMatch(node -> node.kind() == ImpactGraphNodeKind.TYPE);
    assertThat(report.impactGraph().nodes()).anyMatch(node -> node.kind() == ImpactGraphNodeKind.TEST);
    assertThat(report.impactGraph().nodes())
        .anyMatch(node -> node.kind() == ImpactGraphNodeKind.TEST && node.label().equals("TestA"));
  }

  /**
   * Verifies mapper output is well-formed when no impacted tests are present.
   */
  @Test
  void testToImpactReportHandlesEmptyResult() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper = new ImpactReportMapper(
        Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));

    final ImpactReport report = mapper.toImpactReport(projectPath, Map.of(), Map.of());

    assertThat(report.impactedTestsCount()).isZero();
    assertThat(report.uniqueCausesCount()).isZero();
    assertThat(report.averageCausesPerTest()).isZero();
    assertThat(report.impactedTests()).isEmpty();
    assertThat(report.topCauses()).isEmpty();
    assertThat(report.impactGraph()).isEqualTo(ImpactGraph.empty());
  }

  /**
   * Verifies graph capping is deterministic and records truncation stats when limits are exceeded.
   */
  @Test
  void testToImpactReportAppliesDeterministicGraphCapping() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper = new ImpactReportMapper(
        Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));

    final Map<Path, Set<String>> relevantTestsWithCauses = new LinkedHashMap<>();
    final Map<String, Set<String>> impactedTypeToCauses = new LinkedHashMap<>();

    for (int i = 1; i <= 24; i++) {
      final String cause = "Cause" + i;
      final Path testPath = projectPath.resolve("module/src/test/java/T" + i + "Test.java");
      relevantTestsWithCauses.put(testPath, Set.of(cause));
      impactedTypeToCauses.put("Type" + i, new LinkedHashSet<>(Set.of(cause)));
    }

    final ImpactReport report1 = mapper.toImpactReport(projectPath, relevantTestsWithCauses,
        impactedTypeToCauses);
    final ImpactReport report2 = mapper.toImpactReport(projectPath, relevantTestsWithCauses,
        impactedTypeToCauses);

    assertThat(report1.impactGraph().stats().isTruncated()).isTrue();
    assertThat(report1.impactGraph().stats().shownNodes()).isLessThanOrEqualTo(80);
    assertThat(report1.impactGraph().nodes()).isEqualTo(report2.impactGraph().nodes());
    assertThat(report1.impactGraph().edges()).isEqualTo(report2.impactGraph().edges());
  }

  /**
   * Verifies direct and transitive-style inputs both produce lane-complete graph node kinds.
   */
  @Test
  void testToImpactReportBuildsGraphForDirectAndTransitiveLikeInputs() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper = new ImpactReportMapper(
        Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));

    final Map<Path, Set<String>> relevantTestsWithCauses = Map.of(
        projectPath.resolve("module/src/test/java/FooTest.java"), Set.of("FooService"));

    final ImpactReport direct = mapper.toImpactReport(projectPath, relevantTestsWithCauses,
        Map.of("FooService", Set.of("FooService")));
    final ImpactReport transitive = mapper.toImpactReport(projectPath, relevantTestsWithCauses,
        Map.of("FooFacade", Set.of("FooService"), "FooService", Set.of("FooService")));

    assertThat(direct.impactGraph().nodes()).anyMatch(node -> node.kind() == ImpactGraphNodeKind.CAUSE);
    assertThat(direct.impactGraph().nodes()).anyMatch(node -> node.kind() == ImpactGraphNodeKind.TEST);
    assertThat(transitive.impactGraph().nodes()).anyMatch(node -> node.kind() == ImpactGraphNodeKind.TYPE);
  }
}
