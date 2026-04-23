package io.github.hillemacher.testimpactchecker.gradle;

import io.github.hillemacher.testimpactchecker.ImpactDetectionReportData;
import io.github.hillemacher.testimpactchecker.TestImpactChecker;
import io.github.hillemacher.testimpactchecker.cli.output.JsonOutputFormatter;
import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import io.github.hillemacher.testimpactchecker.report.HtmlImpactReportRenderer;
import io.github.hillemacher.testimpactchecker.report.ImpactReport;
import io.github.hillemacher.testimpactchecker.report.ImpactReportMapper;
import io.github.hillemacher.testimpactchecker.report.ImpactReportWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Gradle task that runs the Test Impact Checker against the current project and writes the JSON +
 * HTML artifacts.
 *
 * <p>All config fields are lazy {@link Property}/{@link ListProperty} instances so the task stays
 * configuration-cache compatible. Because the analysis depends on Git state that is not captured as
 * an input, the task is forced not-up-to-date via {@code outputs.upToDateWhen(false)} set by the
 * plugin at registration time.
 */
public abstract class TestImpactCheckTask extends DefaultTask {

  @Input
  public abstract Property<String> getBaseRef();

  @Input
  public abstract Property<String> getTargetRef();

  @Input
  public abstract ListProperty<String> getAnnotations();

  @Input
  public abstract Property<AnalysisMode> getAnalysisMode();

  @Input
  public abstract Property<MockPolicy> getMockPolicy();

  @Input
  public abstract Property<Integer> getMaxPropagationDepth();

  @Input
  public abstract Property<TestImpactChecker.CacheMode> getCacheMode();

  @Internal
  public abstract DirectoryProperty getProjectDirectory();

  @Internal
  public abstract DirectoryProperty getCacheDirectory();

  @OutputFile
  public abstract RegularFileProperty getImpactJsonOutput();

  @OutputFile
  public abstract RegularFileProperty getImpactHtmlOutput();

  @TaskAction
  public void run() throws IOException {
    final Path projectPath = getProjectDirectory().get().getAsFile().toPath();
    final Path cacheDir = getCacheDirectory().get().getAsFile().toPath();
    final Path jsonOutput = getImpactJsonOutput().get().getAsFile().toPath();
    final Path htmlOutput = getImpactHtmlOutput().get().getAsFile().toPath();

    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setBaseRef(getBaseRef().get());
    config.setTargetRef(getTargetRef().get());
    config.setAnnotations(getAnnotations().get());
    config.setAnalysisMode(getAnalysisMode().get());
    config.setMockPolicy(getMockPolicy().get());
    config.setMaxPropagationDepth(getMaxPropagationDepth().get());
    config.setCacheDirectoryPath(cacheDir.toAbsolutePath().toString());

    final TestImpactChecker checker = new TestImpactChecker();
    final ImpactDetectionReportData data =
        checker.detectImpactReportData(projectPath, config, getCacheMode().get());

    writeJson(projectPath, data, jsonOutput);
    writeHtml(projectPath, data, htmlOutput, config);

    getLogger()
        .lifecycle(
            "testImpactCheck: {} impacted test(s), wrote {} and {}",
            data.relevantTestsWithCauses().size(),
            projectPath.relativize(jsonOutput),
            projectPath.relativize(htmlOutput));
  }

  private static void writeJson(
      final Path projectPath, final ImpactDetectionReportData data, final Path jsonOutput)
      throws IOException {
    Files.createDirectories(jsonOutput.getParent());
    try (final BufferedWriter writer =
        Files.newBufferedWriter(jsonOutput, StandardCharsets.UTF_8)) {
      new JsonOutputFormatter().write(projectPath, data, writer);
    }
  }

  private static void writeHtml(
      final Path projectPath,
      final ImpactDetectionReportData data,
      final Path htmlOutput,
      final ImpactCheckerConfig config)
      throws IOException {
    final ImpactReportWriter writer = new ImpactReportWriter();
    final ImpactReportMapper mapper = new ImpactReportMapper();
    final HtmlImpactReportRenderer renderer = new HtmlImpactReportRenderer();
    final ImpactReport report =
        mapper.toImpactReport(
            projectPath,
            projectPath,
            config,
            ZoneId.systemDefault(),
            data.relevantTestsWithCauses(),
            data.impactedTypeToCauses());
    final String html = renderer.render(report);
    writer.writeReport(htmlOutput, html);
  }
}
