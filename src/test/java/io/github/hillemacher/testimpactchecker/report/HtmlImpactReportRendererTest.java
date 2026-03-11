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
        List.of(new CauseSummaryEntry("Ca&use\"", 1)));

    final String html = renderer.render(report);

    assertThat(html).contains("Test Impact Report");
    assertThat(html).contains("Impacted tests and causes");
    assertThat(html).contains("Top causes");
    assertThat(html).contains("module/Test&lt;Evil&gt;.java");
    assertThat(html).contains("Ca&amp;use&quot;");
    assertThat(html).contains("/tmp/project&lt;&amp;&gt;&quot;&#39;");
    assertThat(html).doesNotContain("module/Test<Evil>.java");
  }

  /**
   * Verifies empty impact reports display an explicit empty-state message.
   */
  @Test
  void testRenderShowsEmptyStateForNoImpacts() {
    final HtmlImpactReportRenderer renderer = new HtmlImpactReportRenderer();
    final ImpactReport report = new ImpactReport(
        Instant.parse("2026-03-11T09:15:00Z"), Path.of("/tmp/project"), 0, 0, 0,
        List.of(), List.of());

    final String html = renderer.render(report);

    assertThat(html).contains("None found.");
  }
}
