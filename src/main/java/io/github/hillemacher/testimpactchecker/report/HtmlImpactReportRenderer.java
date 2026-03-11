package io.github.hillemacher.testimpactchecker.report;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders an {@link ImpactReport} as a static dependency-free HTML page.
 */
public class HtmlImpactReportRenderer {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
          .withLocale(Locale.ROOT)
          .withZone(ZoneOffset.UTC);

  /**
   * Renders report content as a complete HTML document.
   *
   * @param report immutable report model to render
   * @return complete HTML document string
   * @throws NullPointerException if {@code report} is {@code null}
   */
  public String render(final ImpactReport report) {
    Objects.requireNonNull(report, "report must not be null");

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
    html.append("      <p class=\"meta\">Project: ")
        .append(escapeHtml(report.projectPath().toString()))
        .append("</p>\n");
    html.append("      <p class=\"meta\">Generated: ")
        .append(escapeHtml(DATE_TIME_FORMATTER.format(report.generatedAt())))
        .append("</p>\n");
    html.append("    </header>\n");

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

    html.append("  </main>\n");
    html.append("</body>\n");
    html.append("</html>\n");
    return html.toString();
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
        @media (max-width: 640px) {
          h1 {
            font-size: 1.6rem;
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
