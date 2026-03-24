package io.github.hillemacher.testimpactchecker.report;

import gg.jte.Content;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;

/** Immutable template model used to render the static HTML impact report. */
public record HtmlImpactReportTemplateModel(
    @NonNull ImpactReport report,
    @NonNull String formattedGeneratedTimestamp,
    @NonNull Map<String, String> causeTokens,
    @NonNull Content inlineCss,
    @NonNull Content inlineScript,
    @NonNull Content overviewGraphMarkup,
    @NonNull List<RenderedCauseGraphSection> renderedCauseGraphSections) {

  public String displayOrDash(final Optional<String> value) {
    return value.orElse("\u2014");
  }

  public String annotationsOrDash() {
    return report.metadata().annotations().isEmpty()
        ? "\u2014"
        : String.join(", ", report.metadata().annotations());
  }

  public boolean hasConfigPath() {
    return report.metadata().configPath().isPresent();
  }

  public String configPath() {
    return report.metadata().configPath().orElseThrow().toString();
  }

  public String joinCausesAttributeValue(final List<String> causes) {
    final StringBuilder joinedTokens = new StringBuilder();
    for (int index = 0; index < causes.size(); index++) {
      if (index > 0) {
        joinedTokens.append('|');
      }
      joinedTokens.append(causeTokens.get(causes.get(index)));
    }
    return joinedTokens.toString();
  }

  /** Pre-rendered per-cause graph details inserted as trusted HTML/SVG fragments. */
  public record RenderedCauseGraphSection(
      @NonNull String cause,
      int impactedTypeCount,
      int impactedTestCount,
      @NonNull Content graphMarkup) {}
}
