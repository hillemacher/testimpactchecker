package io.github.hillemacher.testimpactchecker.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
        Instant.parse("2026-03-11T09:15:00Z"),
        Path.of("/tmp/project<&>\"'"),
        1,
        1,
        1,
        List.of(new ImpactedTestEntry(Path.of("module/Test<Evil>.java"), List.of("Ca&use\""))),
        List.of(new CauseSummaryEntry("Ca&use\"", 1)),
        new ImpactGraph(
            List.of(
                new ImpactGraphNode("cause|Ca&use\"", "Ca&use\"", ImpactGraphNodeKind.CAUSE, 1),
                new ImpactGraphNode("type|Fa<cade>", "Fa<cade>", ImpactGraphNodeKind.TYPE, 1),
                new ImpactGraphNode("test|module/Test<Evil>.java", "module/Test<Evil>.java",
                    ImpactGraphNodeKind.TEST, 1)),
            List.of(
                new ImpactGraphEdge("cause|Ca&use\"", "type|Fa<cade>"),
                new ImpactGraphEdge("type|Fa<cade>", "test|module/Test<Evil>.java")),
            new ImpactGraphStats(3, 3, 2, 2)));

    final String html = renderer.render(report);

    assertThat(html).contains("Test Impact Report");
    assertThat(html).contains("Impacted tests and causes");
    assertThat(html).contains("Top causes");
    assertThat(html).contains("Impact graph");
    assertThat(html).contains("impact-graph-svg");
    assertThat(html).contains("module/Test&lt;Evil&gt;.java");
    assertThat(html).contains("Ca&amp;use&quot;");
    assertThat(html).contains("Fa&lt;cade&gt;");
    assertThat(html).contains("/tmp/project&lt;&amp;&gt;&quot;&#39;");
    assertThat(html).doesNotContain("module/Test<Evil>.java");
  }

  /**
   * Verifies empty impact reports display explicit empty-state messages.
   */
  @Test
  void testRenderShowsEmptyStateForNoImpacts() {
    final HtmlImpactReportRenderer renderer = new HtmlImpactReportRenderer();
    final ImpactReport report = new ImpactReport(
        Instant.parse("2026-03-11T09:15:00Z"), Path.of("/tmp/project"), 0, 0, 0,
        List.of(), List.of(), ImpactGraph.empty());

    final String html = renderer.render(report);

    assertThat(html).contains("None found.");
    assertThat(html).contains("No impact graph data available.");
  }

  /**
   * Verifies truncation metadata is rendered for capped graph outputs.
   */
  @Test
  void testRenderShowsGraphTruncationMessage() {
    final HtmlImpactReportRenderer renderer = new HtmlImpactReportRenderer();
    final ImpactReport report = new ImpactReport(
        Instant.parse("2026-03-11T09:15:00Z"), Path.of("/tmp/project"), 2, 2, 1.5,
        List.of(new ImpactedTestEntry(Path.of("A.java"), List.of("A"))),
        List.of(new CauseSummaryEntry("A", 2)),
        new ImpactGraph(
            List.of(new ImpactGraphNode("cause|A", "A", ImpactGraphNodeKind.CAUSE, 2)),
            List.of(),
            new ImpactGraphStats(120, 80, 300, 190)));

    final String html = renderer.render(report);

    assertThat(html).contains("Showing 80/120 nodes and 190/300 edges.");
  }
}
