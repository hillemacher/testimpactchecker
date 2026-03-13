package io.github.hillemacher.testimpactchecker.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests deterministic mapping from raw impact data to the immutable HTML report model. */
class ImpactReportMapperTest {

  /** Verifies sorting and aggregate metrics are stable for a non-empty impact set. */
  @Test
  void testToImpactReportBuildsDeterministicSortedModel() {
    final Path projectPath = Path.of("/tmp/project");
    final Path configPath = Path.of("/tmp/configs/checker.json");
    final ImpactReportMapper mapper =
        new ImpactReportMapper(Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));
    final ImpactCheckerConfig config = createConfig();

    final Map<Path, Set<String>> relevantTestsWithCauses =
        Map.of(
            projectPath.resolve("module/src/test/java/b/TestB.java"), Set.of("BClass", "AClass"),
            projectPath.resolve("module/src/test/java/a/TestA.java"), Set.of("AClass"));
    final Map<String, Set<String>> impactedTypeToCauses =
        Map.of(
            "AClass", Set.of("AClass"),
            "BClass", Set.of("BClass"),
            "FacadeType", Set.of("AClass", "BClass"));

    final ImpactReport report =
        mapper.toImpactReport(
            projectPath,
            configPath,
            config,
            ZoneId.of("Europe/Berlin"),
            relevantTestsWithCauses,
            impactedTypeToCauses);

    assertThat(report.metadata().generatedAtUtc()).isEqualTo(Instant.parse("2026-03-11T09:15:00Z"));
    assertThat(report.metadata().projectPath()).isEqualTo(projectPath);
    assertThat(report.metadata().executionZoneId()).isEqualTo(ZoneId.of("Europe/Berlin"));
    assertThat(report.metadata().baseRef()).contains("refs/heads/main");
    assertThat(report.metadata().targetRef()).contains("HEAD");
    assertThat(report.metadata().annotations())
        .containsExactly("ContextConfiguration", "IntegrationTest");
    assertThat(report.metadata().analysisMode()).isEqualTo(AnalysisMode.TRANSITIVE);
    assertThat(report.metadata().maxPropagationDepth()).isEqualTo(4);
    assertThat(report.metadata().mockPolicy()).isEqualTo(MockPolicy.FILTER_MOCKED_PATHS);
    assertThat(report.metadata().configPath()).contains(configPath.toAbsolutePath().normalize());
    assertThat(report.impactedTestsCount()).isEqualTo(2);
    assertThat(report.uniqueCausesCount()).isEqualTo(2);
    assertThat(report.averageCausesPerTest()).isEqualTo(1.5);

    assertThat(report.impactedTests())
        .extracting(entry -> entry.relativeTestPath().toString())
        .containsExactly("module/src/test/java/a/TestA.java", "module/src/test/java/b/TestB.java");
    assertThat(report.impactedTests().get(1).causes()).containsExactly("AClass", "BClass");

    assertThat(report.topCauses())
        .containsExactly(new CauseSummaryEntry("AClass", 2), new CauseSummaryEntry("BClass", 1));

    assertThat(report.graphBundle().fullGraph().nodes()).isNotEmpty();
    assertThat(report.graphBundle().fullGraph().edges()).isNotEmpty();
    assertThat(report.graphBundle().fullGraph().nodes())
        .anyMatch(node -> node.kind() == ImpactGraphNodeKind.CAUSE);
    assertThat(report.graphBundle().fullGraph().nodes())
        .anyMatch(node -> node.kind() == ImpactGraphNodeKind.TYPE);
    assertThat(report.graphBundle().fullGraph().nodes())
        .anyMatch(node -> node.kind() == ImpactGraphNodeKind.TEST);
    assertThat(report.graphBundle().fullGraph().nodes())
        .anyMatch(node -> node.kind() == ImpactGraphNodeKind.TEST && node.label().equals("TestA"));
    assertThat(report.graphBundle().causeSections())
        .extracting(ImpactGraphSection::cause)
        .containsExactly("AClass", "BClass");
  }

  /** Verifies mapper output is well-formed when no impacted tests are present. */
  @Test
  void testToImpactReportHandlesEmptyResult() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper =
        new ImpactReportMapper(Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));

    final ImpactReport report =
        mapper.toImpactReport(
            projectPath, null, createConfig(), ZoneId.of("UTC"), Map.of(), Map.of());

    assertThat(report.impactedTestsCount()).isZero();
    assertThat(report.uniqueCausesCount()).isZero();
    assertThat(report.averageCausesPerTest()).isZero();
    assertThat(report.impactedTests()).isEmpty();
    assertThat(report.topCauses()).isEmpty();
    assertThat(report.graphBundle()).isEqualTo(ImpactGraphBundle.empty());
    assertThat(report.metadata().configPath()).isEmpty();
  }

  /**
   * Verifies graph capping is deterministic and records truncation stats when limits are exceeded.
   */
  @Test
  void testToImpactReportAppliesDeterministicGraphCapping() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper =
        new ImpactReportMapper(Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));

    final Map<Path, Set<String>> relevantTestsWithCauses = new LinkedHashMap<>();
    final Map<String, Set<String>> impactedTypeToCauses = new LinkedHashMap<>();
    final Set<String> allCauses = new LinkedHashSet<>();

    for (int i = 1; i <= 24; i++) {
      allCauses.add("Cause" + i);
    }

    for (int i = 1; i <= 24; i++) {
      final String cause = "Cause" + i;
      final Path testPath = projectPath.resolve("module/src/test/java/T" + i + "Test.java");
      relevantTestsWithCauses.put(testPath, allCauses);
      impactedTypeToCauses.put("Type" + i, new LinkedHashSet<>(Set.of(cause)));
    }

    final ImpactReport report1 =
        mapper.toImpactReport(
            projectPath,
            null,
            createConfig(),
            ZoneId.of("UTC"),
            relevantTestsWithCauses,
            impactedTypeToCauses);
    final ImpactReport report2 =
        mapper.toImpactReport(
            projectPath,
            null,
            createConfig(),
            ZoneId.of("UTC"),
            relevantTestsWithCauses,
            impactedTypeToCauses);

    assertThat(report1.graphBundle().fullGraph().stats().isTruncated()).isTrue();
    assertThat(report1.graphBundle().fullGraph().stats().shownNodes()).isLessThanOrEqualTo(80);
    assertThat(report1.graphBundle().fullGraph().nodes())
        .isEqualTo(report2.graphBundle().fullGraph().nodes());
    assertThat(report1.graphBundle().fullGraph().edges())
        .isEqualTo(report2.graphBundle().fullGraph().edges());
    assertThat(report1.graphBundle().overviewGraph().nodes())
        .isEqualTo(report1.graphBundle().fullGraph().nodes());
    assertThat(report1.graphBundle().overviewGraph().edges().size())
        .isLessThan(report1.graphBundle().fullGraph().edges().size());
    assertThat(report1.graphBundle().causeSections())
        .extracting(ImpactGraphSection::cause)
        .isEqualTo(
            report2.graphBundle().causeSections().stream().map(ImpactGraphSection::cause).toList());
  }

  /** Verifies direct and transitive-style inputs both produce lane-complete graph node kinds. */
  @Test
  void testToImpactReportBuildsGraphForDirectAndTransitiveLikeInputs() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper =
        new ImpactReportMapper(Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));

    final Map<Path, Set<String>> relevantTestsWithCauses =
        Map.of(projectPath.resolve("module/src/test/java/FooTest.java"), Set.of("FooService"));

    final ImpactReport direct =
        mapper.toImpactReport(
            projectPath,
            null,
            createConfig(),
            ZoneId.of("UTC"),
            relevantTestsWithCauses,
            Map.of("FooService", Set.of("FooService")));
    final ImpactReport transitive =
        mapper.toImpactReport(
            projectPath,
            null,
            createConfig(),
            ZoneId.of("UTC"),
            relevantTestsWithCauses,
            Map.of("FooFacade", Set.of("FooService"), "FooService", Set.of("FooService")));

    assertThat(direct.graphBundle().fullGraph().nodes())
        .anyMatch(node -> node.kind() == ImpactGraphNodeKind.CAUSE);
    assertThat(direct.graphBundle().fullGraph().nodes())
        .anyMatch(node -> node.kind() == ImpactGraphNodeKind.TEST);
    assertThat(transitive.graphBundle().fullGraph().nodes())
        .anyMatch(node -> node.kind() == ImpactGraphNodeKind.TYPE);
  }

  /** Verifies annotations are sorted deterministically and blank refs are omitted from metadata. */
  @Test
  void testToImpactReportNormalizesMetadataValues() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper =
        new ImpactReportMapper(Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setAnnotations(List.of("Zeta", "Alpha"));
    config.setBaseRef(" ");
    config.setTargetRef(null);

    final ImpactReport report =
        mapper.toImpactReport(
            projectPath,
            Path.of("/tmp/configs/report.json"),
            config,
            ZoneId.of("Europe/Berlin"),
            Map.of(),
            Map.of());

    assertThat(report.metadata().annotations()).containsExactly("Alpha", "Zeta");
    assertThat(report.metadata().baseRef()).isEmpty();
    assertThat(report.metadata().targetRef()).isEmpty();
  }

  /** Verifies per-cause graphs contain only nodes and edges connected to their cause. */
  @Test
  void testToImpactReportBuildsFocusedPerCauseGraphs() {
    final Path projectPath = Path.of("/tmp/project");
    final ImpactReportMapper mapper =
        new ImpactReportMapper(Clock.fixed(Instant.parse("2026-03-11T09:15:00Z"), ZoneOffset.UTC));

    final Map<Path, Set<String>> relevantTestsWithCauses =
        Map.of(
            projectPath.resolve("module/src/test/java/FooTest.java"), Set.of("FooService"),
            projectPath.resolve("module/src/test/java/BarTest.java"), Set.of("BarService"));
    final Map<String, Set<String>> impactedTypeToCauses =
        Map.of(
            "FooFacade", Set.of("FooService"),
            "BarFacade", Set.of("BarService"));

    final ImpactReport report =
        mapper.toImpactReport(
            projectPath,
            null,
            createConfig(),
            ZoneId.of("UTC"),
            relevantTestsWithCauses,
            impactedTypeToCauses);

    final ImpactGraphSection fooSection =
        report.graphBundle().causeSections().stream()
            .filter(section -> section.cause().equals("FooService"))
            .findFirst()
            .orElseThrow();

    assertThat(fooSection.impactedTypeCount()).isEqualTo(1);
    assertThat(fooSection.impactedTestCount()).isEqualTo(1);
    assertThat(fooSection.graph().nodes())
        .extracting(ImpactGraphNode::label)
        .containsExactlyInAnyOrder("FooService", "FooFacade", "FooTest");
    assertThat(fooSection.graph().edges())
        .containsExactlyInAnyOrder(
            new ImpactGraphEdge("cause|FooService", "type|FooFacade"),
            new ImpactGraphEdge("type|FooFacade", "test|module/src/test/java/FooTest.java"));
  }

  private ImpactCheckerConfig createConfig() {
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setAnnotations(List.of("IntegrationTest", "ContextConfiguration"));
    config.setBaseRef("refs/heads/main");
    config.setTargetRef("HEAD");
    config.setAnalysisMode(AnalysisMode.TRANSITIVE);
    config.setMaxPropagationDepth(4);
    config.setMockPolicy(MockPolicy.FILTER_MOCKED_PATHS);
    return config;
  }
}
