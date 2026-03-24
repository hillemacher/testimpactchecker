package io.github.hillemacher.testimpactchecker.report;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.NonNull;

/**
 * Renders an {@link ImpactReport} as a static dependency-free HTML page via precompiled templates.
 */
public class HtmlImpactReportRenderer {

  private static final String TEMPLATE_NAME = "report/impactReport.jte";
  private static final String CSS_RESOURCE_PATH = "/report/html-impact-report.css";
  private static final String SCRIPT_RESOURCE_PATH = "/report/html-impact-report.js";
  private static final TemplateEngine TEMPLATE_ENGINE =
      TemplateEngine.createPrecompiled(ContentType.Html);
  private static final DateTimeFormatter UTC_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
          .withLocale(Locale.ROOT)
          .withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withLocale(Locale.ROOT);

  private final ImpactGraphSvgRenderer impactGraphSvgRenderer = new ImpactGraphSvgRenderer();

  /**
   * Renders report content as a complete HTML document.
   *
   * @param report immutable report model to render
   * @return complete HTML document string
   * @throws NullPointerException if {@code report} is {@code null}
   */
  public String render(@NonNull final ImpactReport report) {
    final HtmlImpactReportTemplateModel templateModel = toTemplateModel(report);
    final StringOutput output = new StringOutput();
    TEMPLATE_ENGINE.render(TEMPLATE_NAME, templateModel, output);
    return output.toString();
  }

  private HtmlImpactReportTemplateModel toTemplateModel(final ImpactReport report) {
    return new HtmlImpactReportTemplateModel(
        report,
        formatGeneratedTimestamp(
            report.metadata().generatedAtUtc(), report.metadata().executionZoneId()),
        createCauseTokens(report),
        rawHtmlContent(readResource(CSS_RESOURCE_PATH)),
        rawHtmlContent(readResource(SCRIPT_RESOURCE_PATH)),
        rawHtmlContent(impactGraphSvgRenderer.render(report.graphBundle().overviewGraph())),
        report.graphBundle().causeSections().stream()
            .map(
                section ->
                    new HtmlImpactReportTemplateModel.RenderedCauseGraphSection(
                        section.cause(),
                        section.impactedTypeCount(),
                        section.impactedTestCount(),
                        rawHtmlContent(impactGraphSvgRenderer.render(section.graph()))))
            .toList());
  }

  private String formatGeneratedTimestamp(
      final Instant generatedAtUtc, final ZoneId executionZoneId) {
    return LOCAL_DATE_TIME_FORMATTER.withZone(executionZoneId).format(generatedAtUtc)
        + " ("
        + UTC_DATE_TIME_FORMATTER.format(generatedAtUtc)
        + ")";
  }

  private Set<String> collectUniqueCauses(final ImpactReport report) {
    final Set<String> uniqueCauses = new TreeSet<>();
    report.impactedTests().forEach(entry -> uniqueCauses.addAll(entry.causes()));
    return uniqueCauses;
  }

  private Map<String, String> createCauseTokens(final ImpactReport report) {
    final Map<String, String> causeTokens = new LinkedHashMap<>();
    int tokenIndex = 0;
    for (final String cause : collectUniqueCauses(report)) {
      causeTokens.put(cause, toCauseToken(cause, tokenIndex));
      tokenIndex++;
    }
    return causeTokens;
  }

  private String toCauseToken(final String cause, final int tokenIndex) {
    final StringBuilder token = new StringBuilder();
    for (int characterIndex = 0; characterIndex < cause.length(); characterIndex++) {
      final char character = cause.charAt(characterIndex);
      if (Character.isLetterOrDigit(character)) {
        token.append(Character.toLowerCase(character));
      } else {
        token.append('-');
      }
    }
    return token.append('-').append(tokenIndex).toString();
  }

  private RawHtmlContent rawHtmlContent(final String value) {
    return new RawHtmlContent(value);
  }

  private String readResource(final String resourcePath) {
    try (InputStream inputStream =
        HtmlImpactReportRenderer.class.getResourceAsStream(resourcePath)) {
      if (inputStream == null) {
        throw new IllegalStateException("Missing report resource: " + resourcePath);
      }
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read report resource: " + resourcePath, e);
    }
  }
}
