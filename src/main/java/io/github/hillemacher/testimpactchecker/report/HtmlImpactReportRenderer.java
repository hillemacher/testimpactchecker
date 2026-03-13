package io.github.hillemacher.testimpactchecker.report;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.NonNull;

/**
 * Renders an {@link ImpactReport} as a static dependency-free HTML page.
 */
public class HtmlImpactReportRenderer {

  private static final DateTimeFormatter UTC_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
          .withLocale(Locale.ROOT)
          .withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
          .withLocale(Locale.ROOT);
  private final ImpactGraphSvgRenderer impactGraphSvgRenderer = new ImpactGraphSvgRenderer();

  /**
   * Renders report content as a complete HTML document.
   *
   * @param report immutable report model to render
   * @return complete HTML document string
   * @throws NullPointerException if {@code report} is {@code null}
   */
  public String render(@NonNull final ImpactReport report) {
    final StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html>\n");
    html.append("<html lang=\"en\">\n");
    html.append("<head>\n");
    html.append("  <meta charset=\"UTF-8\">\n");
    html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
    html.append("  <title>Test Impact Report</title>\n");
    html.append("  <style>\n");
    html.append(css());
    html.append("  </style>\n");
    html.append("</head>\n");
    html.append("<body>\n");
    html.append("  <main class=\"container\">\n");
    html.append("    <header class=\"page-header\">\n");
    html.append("      <h1>Test Impact Report</h1>\n");
    html.append("    </header>\n");

    renderMetadataSection(report, html);

    html.append("    <section class=\"card-section\">\n");
    html.append("      <article class=\"metric-card\"><h2>Impacted tests</h2><p>")
        .append(report.impactedTestsCount())
        .append("</p></article>\n");
    html.append("      <article class=\"metric-card\"><h2>Unique causes</h2><p>")
        .append(report.uniqueCausesCount())
        .append("</p></article>\n");
    html.append("      <article class=\"metric-card\"><h2>Avg causes/test</h2><p>")
        .append(String.format(Locale.ROOT, "%.2f", report.averageCausesPerTest()))
        .append("</p></article>\n");
    html.append("    </section>\n");

    renderImpactedTestsSection(report, html);
    renderTopCausesSection(report, html);
    renderImpactGraphSection(report, html);

    html.append("  </main>\n");
    html.append("</body>\n");
    html.append("</html>\n");
    return html.toString();
  }

  private void renderMetadataSection(final ImpactReport report, final StringBuilder html) {
    final ImpactReportMetadata metadata = report.metadata();
    html.append("    <section>\n");
    html.append("      <h2>Run metadata</h2>\n");
    html.append("      <dl class=\"metadata-grid\">\n");
    appendMetadataItem(html, "Project", metadata.projectPath().toString());
    appendMetadataItem(html, "Generated", formatGeneratedTimestamp(
        metadata.generatedAtUtc(), metadata.executionZoneId()));
    appendMetadataItem(html, "Execution zone", metadata.executionZoneId().getId());
    appendMetadataItem(html, "Base ref", metadata.baseRef().orElse("\u2014"));
    appendMetadataItem(html, "Target ref", metadata.targetRef().orElse("\u2014"));
    appendMetadataItem(html, "Annotations",
        metadata.annotations().isEmpty() ? "\u2014" : String.join(", ", metadata.annotations()));
    appendMetadataItem(html, "Analysis mode", metadata.analysisMode().name());
    appendMetadataItem(html, "Max propagation depth", Integer.toString(metadata.maxPropagationDepth()));
    appendMetadataItem(html, "Mock policy", metadata.mockPolicy().name());
    metadata.configPath()
        .ifPresent(configPath -> appendMetadataItem(html, "Config path", configPath.toString()));
    html.append("      </dl>\n");
    html.append("    </section>\n");
  }

  private void appendMetadataItem(final StringBuilder html, final String label, final String value) {
    html.append("        <dt>").append(escapeHtml(label)).append("</dt>\n");
    html.append("        <dd>").append(escapeHtml(value)).append("</dd>\n");
  }

  private String formatGeneratedTimestamp(final Instant generatedAtUtc, final ZoneId executionZoneId) {
    return LOCAL_DATE_TIME_FORMATTER.withZone(executionZoneId).format(generatedAtUtc)
        + " ("
        + UTC_DATE_TIME_FORMATTER.format(generatedAtUtc)
        + ")";
  }

  private void renderImpactedTestsSection(final ImpactReport report, final StringBuilder html) {
    html.append("    <section>\n");
    html.append("      <h2>Impacted tests and causes</h2>\n");
    if (report.impactedTests().isEmpty()) {
      html.append("      <p class=\"empty-state\">None found.</p>\n");
      html.append("    </section>\n");
      return;
    }

    html.append("      <div class=\"table-wrapper\">\n");
    html.append("      <table>\n");
    html.append("        <thead><tr><th>Test path</th><th>Causes</th></tr></thead>\n");
    html.append("        <tbody>\n");
    report.impactedTests().forEach(entry -> {
      html.append("          <tr><td>")
          .append(escapeHtml(entry.relativeTestPath().toString()))
          .append("</td><td>")
          .append(escapeHtml(String.join(", ", entry.causes())))
          .append("</td></tr>\n");
    });
    html.append("        </tbody>\n");
    html.append("      </table>\n");
    html.append("      </div>\n");
    html.append("    </section>\n");
  }

  private void renderTopCausesSection(final ImpactReport report, final StringBuilder html) {
    html.append("    <section>\n");
    html.append("      <h2>Top causes</h2>\n");
    if (report.topCauses().isEmpty()) {
      html.append("      <p class=\"empty-state\">None found.</p>\n");
      html.append("    </section>\n");
      return;
    }

    html.append("      <div class=\"table-wrapper\">\n");
    html.append("      <table>\n");
    html.append("        <thead><tr><th>Cause</th><th>Impacted tests</th></tr></thead>\n");
    html.append("        <tbody>\n");
    report.topCauses().forEach(cause -> {
      html.append("          <tr><td>")
          .append(escapeHtml(cause.cause()))
          .append("</td><td>")
          .append(cause.impactedTestCount())
          .append("</td></tr>\n");
    });
    html.append("        </tbody>\n");
    html.append("      </table>\n");
    html.append("      </div>\n");
    html.append("    </section>\n");
  }

  private void renderImpactGraphSection(final ImpactReport report, final StringBuilder html) {
    html.append("    <section>\n");
    html.append("      <h2>Impact graph</h2>\n");
    html.append("      <p class=\"meta\">Focused, capped graph (causes -> impacted types -> impacted tests)</p>\n");
    html.append("      <p class=\"legend\">");
    html.append("<span class=\"legend-chip legend-cause\">Changed causes</span> ");
    html.append("<span class=\"legend-chip legend-type\">Impacted types</span> ");
    html.append("<span class=\"legend-chip legend-test\">Impacted tests</span>");
    html.append("</p>\n");

    final ImpactGraph impactGraph = report.impactGraph();
    if (impactGraph.stats().isTruncated()) {
      html.append("      <p class=\"meta\">Showing ")
          .append(impactGraph.stats().shownNodes())
          .append("/")
          .append(impactGraph.stats().totalNodes())
          .append(" nodes and ")
          .append(impactGraph.stats().shownEdges())
          .append("/")
          .append(impactGraph.stats().totalEdges())
          .append(" edges.</p>\n");
    }
    html.append("      <div class=\"graph-wrapper\">\n");
    html.append(impactGraphSvgRenderer.render(impactGraph));
    html.append("      </div>\n");
    html.append("    </section>\n");
  }

  private String css() {
    return """
        :root {
          --bg: #f4f7f9;
          --surface: #ffffff;
          --surface-alt: #e8eff2;
          --text: #16252d;
          --muted: #47606b;
          --border: #c4d3da;
          --accent: #2b6978;
        }
        * {
          box-sizing: border-box;
        }
        body {
          margin: 0;
          font-family: \"IBM Plex Sans\", \"Segoe UI\", sans-serif;
          color: var(--text);
          background: linear-gradient(160deg, #f9fbfc 0%, #edf3f6 100%);
        }
        .container {
          max-width: 1100px;
          margin: 0 auto;
          padding: 2rem 1rem 3rem;
        }
        .page-header {
          margin-bottom: 1.5rem;
        }
        h1 {
          margin: 0;
          font-size: 2rem;
        }
        h2 {
          margin: 0 0 0.6rem;
          font-size: 1.2rem;
        }
        .meta {
          margin: 0.3rem 0;
          color: var(--muted);
        }
        .metadata-grid {
          display: grid;
          grid-template-columns: minmax(180px, 240px) 1fr;
          gap: 0.45rem 1rem;
          margin: 0;
        }
        .metadata-grid dt {
          color: var(--muted);
          font-weight: 600;
        }
        .metadata-grid dd,
        .metadata-grid dt {
          margin: 0;
          word-break: break-word;
        }
        section {
          background: var(--surface);
          border: 1px solid var(--border);
          border-radius: 12px;
          padding: 1rem;
          margin-bottom: 1rem;
        }
        .card-section {
          background: transparent;
          border: 0;
          padding: 0;
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
          gap: 0.75rem;
        }
        .metric-card {
          background: var(--surface);
          border: 1px solid var(--border);
          border-radius: 12px;
          padding: 1rem;
        }
        .metric-card p {
          margin: 0;
          font-size: 1.6rem;
          font-weight: 700;
          color: var(--accent);
        }
        .table-wrapper {
          overflow-x: auto;
        }
        table {
          border-collapse: collapse;
          width: 100%;
        }
        th,
        td {
          border: 1px solid var(--border);
          text-align: left;
          vertical-align: top;
          padding: 0.6rem;
        }
        th {
          background: var(--surface-alt);
        }
        .empty-state {
          margin: 0;
          color: var(--muted);
        }
        .graph-wrapper {
          overflow-x: auto;
          border: 1px solid var(--border);
          border-radius: 10px;
          background: #f9fbfc;
          padding: 0.5rem;
        }
        .impact-graph-svg {
          min-width: 980px;
          width: 100%;
          height: auto;
          display: block;
        }
        .graph-edge {
          stroke: #8ba2ad;
          stroke-width: 1.2;
        }
        .graph-node {
          stroke: #607985;
          stroke-width: 1;
        }
        .graph-node-cause {
          fill: #fbe0de;
        }
        .graph-node-type {
          fill: #f9eecf;
        }
        .graph-node-test {
          fill: #d9ebf4;
        }
        .graph-label {
          font-size: 11px;
          fill: #16313d;
          font-family: \"IBM Plex Sans\", \"Segoe UI\", sans-serif;
        }
        .lane-header {
          font-size: 12px;
          fill: #365462;
          font-weight: 700;
          font-family: \"IBM Plex Sans\", \"Segoe UI\", sans-serif;
        }
        .legend {
          margin: 0.5rem 0 0.8rem;
        }
        .legend-chip {
          display: inline-block;
          border: 1px solid var(--border);
          border-radius: 999px;
          padding: 0.15rem 0.55rem;
          margin-right: 0.4rem;
          font-size: 0.78rem;
        }
        .legend-cause {
          background: #fbe0de;
        }
        .legend-type {
          background: #f9eecf;
        }
        .legend-test {
          background: #d9ebf4;
        }
        @media (max-width: 640px) {
          h1 {
            font-size: 1.6rem;
          }
          .metadata-grid {
            grid-template-columns: 1fr;
          }
          .metric-card p {
            font-size: 1.3rem;
          }
        }
        """;
  }

  private String escapeHtml(final String raw) {
    // Escape all user-controlled strings before injecting into HTML.
    return raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
