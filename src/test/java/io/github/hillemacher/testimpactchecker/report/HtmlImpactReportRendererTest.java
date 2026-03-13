package io.github.hillemacher.testimpactchecker.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests static HTML rendering, including escaping and empty-state output.
 */
class HtmlImpactReportRendererTest {

  /**
   * Verifies core sections are rendered and dynamic values are safely escaped.
   */
  @Test
  void testRenderIncludesSectionsAndEscapesDynamicValues() {
    final HtmlImpactReportRenderer renderer = new HtmlImpactReportRenderer();
    final ImpactReport report = new ImpactReport(
        createMetadata(
            Path.of("/tmp/project<&>\"'"),
            Path.of("/tmp/config<&>/checker.json"),
            ZoneId.of("Europe/Berlin")),
        1,
        1,
        1,
        List.of(new ImpactedTestEntry(Path.of("module/Test<Evil>.java"), List.of("Ca&use\""))),
        List.of(new CauseSummaryEntry("Ca&use\"", 1)),
        createGraphBundle("Ca&use\"", "Fa<cade>", "module/Test<Evil>.java"));

    final String html = renderer.render(report);

    assertThat(html).contains("Test Impact Report");
    assertThat(html).contains("Run metadata");
    assertThat(html).contains("Impacted tests and causes");
    assertThat(html).contains("Top causes");
    assertThat(html).contains("Impact graph");
    assertThat(html).contains("impact-graph-svg");
    assertThat(html).contains("Overview");
    assertThat(html).contains("Per-cause detail");
    assertThat(html).contains("<details");
    assertThat(html).contains("Base ref");
    assertThat(html).contains("refs/heads/main&lt;&amp;&gt;");
    assertThat(html).contains("Target ref");
    assertThat(html).contains("HEAD&quot;");
    assertThat(html).contains("Analysis mode");
    assertThat(html).contains("TRANSITIVE");
    assertThat(html).contains("Mock policy");
    assertThat(html).contains("FILTER_MOCKED_PATHS");
    assertThat(html).contains("Execution zone");
    assertThat(html).contains("Europe/Berlin");
    assertThat(html).contains("Generated");
    assertThat(html).contains("2026-03-11 10:15:00 CET (2026-03-11 09:15:00 UTC)");
    assertThat(html).contains("Overview hides 1 lower-signal edges to reduce clutter.");
    assertThat(html).contains("module/Test&lt;Evil&gt;.java");
    assertThat(html).contains("Ca&amp;use&quot;");
    assertThat(html).contains("Fa&lt;cade&gt;");
    assertThat(html).contains("/tmp/project&lt;&amp;&gt;&quot;&#39;");
    assertThat(html).contains("/tmp/config&lt;&amp;&gt;/checker.json");
    assertThat(html).doesNotContain("module/Test<Evil>.java");
  }

  /**
   * Verifies empty impact reports display explicit empty-state messages.
   */
  @Test
  void testRenderShowsEmptyStateForNoImpacts() {
    final HtmlImpactReportRenderer renderer = new HtmlImpactReportRenderer();
    final ImpactReport report = new ImpactReport(
        createMetadata(Path.of("/tmp/project"), null, ZoneId.of("UTC")), 0, 0, 0,
        List.of(), List.of(), ImpactGraphBundle.empty());

    final String html = renderer.render(report);

    assertThat(html).contains("None found.");
    assertThat(html).contains("No impact graph data available.");
    assertThat(html).doesNotContain("Config path");
  }

  /**
   * Verifies truncation metadata is rendered for capped graph outputs.
   */
  @Test
  void testRenderShowsGraphTruncationMessage() {
    final HtmlImpactReportRenderer renderer = new HtmlImpactReportRenderer();
    final ImpactReport report = new ImpactReport(
        createMetadata(Path.of("/tmp/project"), null, ZoneId.of("UTC")), 2, 2, 1.5,
        List.of(new ImpactedTestEntry(Path.of("A.java"), List.of("A"))),
        List.of(new CauseSummaryEntry("A", 2)),
        new ImpactGraphBundle(
            new ImpactGraph(
                List.of(new ImpactGraphNode("cause|A", "A", ImpactGraphNodeKind.CAUSE, 2)),
                List.of(),
                new ImpactGraphStats(120, 80, 300, 190)),
            new ImpactGraph(
                List.of(new ImpactGraphNode("cause|A", "A", ImpactGraphNodeKind.CAUSE, 2)),
                List.of(),
                new ImpactGraphStats(80, 80, 190, 0)),
            List.of()));

    final String html = renderer.render(report);

    assertThat(html).contains("Showing 80/120 nodes and 190/300 edges.");
    assertThat(html).contains("Overview hides 190 lower-signal edges to reduce clutter.");
  }

  private ImpactReportMetadata createMetadata(
      final Path projectPath,
      final Path configPath,
      final ZoneId zoneId) {
    return new ImpactReportMetadata(
        projectPath,
        Instant.parse("2026-03-11T09:15:00Z"),
        zoneId,
        Optional.of("refs/heads/main<&>"),
        Optional.of("HEAD\""),
        List.of("ContextConfiguration", "IntegrationTest"),
        AnalysisMode.TRANSITIVE,
        4,
        MockPolicy.FILTER_MOCKED_PATHS,
        Optional.ofNullable(configPath));
  }

  private ImpactGraphBundle createGraphBundle(
      final String causeLabel,
      final String typeLabel,
      final String testLabel) {
    final ImpactGraph fullGraph = new ImpactGraph(
        List.of(
            new ImpactGraphNode("cause|" + causeLabel, causeLabel, ImpactGraphNodeKind.CAUSE, 1),
            new ImpactGraphNode("type|" + typeLabel, typeLabel, ImpactGraphNodeKind.TYPE, 1),
            new ImpactGraphNode("test|" + testLabel, testLabel, ImpactGraphNodeKind.TEST, 1)),
        List.of(
            new ImpactGraphEdge("cause|" + causeLabel, "type|" + typeLabel),
            new ImpactGraphEdge("type|" + typeLabel, "test|" + testLabel)),
        new ImpactGraphStats(3, 3, 2, 2));
    final ImpactGraph overviewGraph = new ImpactGraph(
        fullGraph.nodes(),
        List.of(new ImpactGraphEdge("cause|" + causeLabel, "type|" + typeLabel)),
        new ImpactGraphStats(3, 3, 2, 1));
    return new ImpactGraphBundle(
        fullGraph,
        overviewGraph,
        List.of(new ImpactGraphSection(causeLabel, 1, 1, fullGraph)));
  }
}
