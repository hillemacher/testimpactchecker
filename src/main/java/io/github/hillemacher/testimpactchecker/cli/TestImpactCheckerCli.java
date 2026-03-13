package io.github.hillemacher.testimpactchecker.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hillemacher.testimpactchecker.ImpactDetectionReportData;
import io.github.hillemacher.testimpactchecker.TestImpactChecker;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.report.HtmlImpactReportRenderer;
import io.github.hillemacher.testimpactchecker.report.ImpactReport;
import io.github.hillemacher.testimpactchecker.report.ImpactReportMapper;
import io.github.hillemacher.testimpactchecker.report.ImpactReportWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Command-line entry point for running test impact detection against a
 * repository.
 */
@Slf4j
public class TestImpactCheckerCli {

  private static final String SIMPLE_LOGGER_DEFAULT_LEVEL =
      "org.slf4j.simpleLogger.defaultLogLevel";
  private static final String SIMPLE_LOGGER_SHOW_DATE_TIME =
      "org.slf4j.simpleLogger.showDateTime";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT =
      "org.slf4j.simpleLogger.dateTimeFormat";
  private static final String SIMPLE_LOGGER_SHOW_THREAD_NAME =
      "org.slf4j.simpleLogger.showThreadName";

  /**
   * Entry point for the ChangedClassTestDetectorCLI command-line tool.
   *
   * <p>
   * This method parses command-line arguments, loads configuration from a JSON
   * file, and
   * determines which test classes are relevant based on detected changes in the
   * project. It prints
   * the list of relevant test files to standard output and logs important steps
   * and errors.
   *
   * <h2>Command-Line Arguments</h2>
   *
   * <ul>
   * <li><b>-p &lt;projectPath&gt;</b> : Path to the root of the project to
   * analyze. (Required)
   * <li><b>-c &lt;configPath&gt;</b> : Path to the JSON configuration file.
   * (Required)
   * <li><b>--html-report &lt;path-or-directory&gt;</b> : Optional path for a static HTML impact
   * report. If a directory is given, {@code impact-report.html} is used. This overrides the
   * optional config field {@code htmlReportOutputPath}.
   * <li><b>-d</b> or <b>--debug</b> : Enables debug logging with detailed diagnostics.
   * <li><b>-h</b> : Shows help and usage information.
   * </ul>
   *
   * <h2>Processing Steps</h2>
   *
   * <ol>
   * <li>Parses CLI arguments and options.
   * <li>If the help option is present, prints usage instructions and exits.
   * <li>Verifies the existence of the project and config paths.
   * <li>Loads analysis configuration from the config file.
   * <li>Executes the test impact analysis using {@link TestImpactChecker}.
   * <li>Prints the relevant tests referencing changed classes to the terminal.
   * <li>Logs errors for missing or invalid options, parse exceptions, or file I/O
   * issues.
   * </ol>
   *
   * @param args the command-line arguments for the application
   */
  public static void main(final String[] args) {
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter formatter = new HelpFormatter();

    final Options options = getOptions();
    boolean success = false;
    try {
      final CommandLine cmd = parser.parse(options, args);
      configureLogging(cmd);

      if (cmd.hasOption("h")) {
        formatter.printHelp("ChangedClassTestDetectorCLI", options, true);
        return;
      }

      log.info("Starting impact analysis");
      // project path
      final Path projectPath = Paths.get(cmd.getOptionValue("p"));
      if (!projectPath.toFile().exists()) {
        log.error("Project path does not exist");
        return;
      }
      log.info("Validated project path {}", projectPath.toAbsolutePath().normalize());

      // config file
      final Path configPath = Paths.get(cmd.getOptionValue("c"));
      if (!configPath.toFile().exists()) {
        log.error("Config path does not exist");
        return;
      }

      final ObjectMapper mapper = new ObjectMapper();
      final ImpactCheckerConfig impactCheckerConfig = mapper.readValue(configPath.toFile(),
          ImpactCheckerConfig.class);
      log.info("Validated config path {}", configPath.toAbsolutePath().normalize());

      final TestImpactChecker testImpactChecker = new TestImpactChecker();
      log.info("Running impact detection");
      final ImpactDetectionReportData impactDetectionReportData = testImpactChecker.detectImpactReportData(
          projectPath.toAbsolutePath().normalize(), impactCheckerConfig);
      final Map<Path, Set<String>> relevantTestsWithCauses = impactDetectionReportData.relevantTestsWithCauses();
      log.info("Impact detection completed: {} impacted tests found", relevantTestsWithCauses.size());

      boolean htmlReportWrittenSuccessfully = true;
      final Optional<String> configuredHtmlReportOutputPath =
          resolveHtmlReportOutputPath(cmd, impactCheckerConfig);
      if (configuredHtmlReportOutputPath.isPresent()) {
        htmlReportWrittenSuccessfully = writeHtmlReport(projectPath, configuredHtmlReportOutputPath.get(),
            impactDetectionReportData);
      }

      System.out.println();
      System.out.println("----------------- ----------------- -----------------");
      System.out.println("Relevant tests and impact causes:");
      if (relevantTestsWithCauses.isEmpty()) {
        System.out.println("None found.");
      } else {
        relevantTestsWithCauses.entrySet().stream()
            .sorted(Comparator.comparing(entry -> toRelativePath(projectPath, entry.getKey()).toString()))
            .forEach(entry -> {
              final Path relativeTestPath = toRelativePath(projectPath, entry.getKey());
              final String causes = entry.getValue().stream()
                  .sorted()
                  .collect(Collectors.joining(", "));
              System.out.println(relativeTestPath);
              System.out.println("  caused by: " + causes);
            });
      }
      success = htmlReportWrittenSuccessfully;
    } catch (final MissingOptionException e) {
      log.error("Missing required option", e);
      formatter.printHelp("ChangedClassTestDetectorCLI", options, true);
    } catch (final ParseException e) {
      log.error("Error parsing command line", e);
      formatter.printHelp("ChangedClassTestDetectorCLI", options, true);
    } catch (final IOException e) {
      log.error("Cannot access config file", e);
    }

    System.out.println("----------------- ----------------- -----------------");
    System.out.println();
    log.info("Finished impact analysis {}", success ? "with success" : "with problems");
  }

  private static Optional<String> resolveHtmlReportOutputPath(
      @NonNull final CommandLine cmd,
      @NonNull final ImpactCheckerConfig impactCheckerConfig) {
    if (cmd.hasOption("html-report")) {
      return Optional.of(cmd.getOptionValue("html-report"));
    }

    if (impactCheckerConfig.getHtmlReportOutputPath() == null
        || impactCheckerConfig.getHtmlReportOutputPath().isBlank()) {
      return Optional.empty();
    }
    return Optional.of(impactCheckerConfig.getHtmlReportOutputPath());
  }

  private static boolean writeHtmlReport(
      @NonNull final Path projectPath,
      @NonNull final String configuredOutputPath,
      @NonNull final ImpactDetectionReportData impactDetectionReportData) {
    final ImpactReportMapper mapper = new ImpactReportMapper();
    final HtmlImpactReportRenderer renderer = new HtmlImpactReportRenderer();
    final ImpactReportWriter writer = new ImpactReportWriter();

    final ImpactReport report = mapper.toImpactReport(
        projectPath,
        impactDetectionReportData.relevantTestsWithCauses(),
        impactDetectionReportData.impactedTypeToCauses());
    final String htmlContent = renderer.render(report);
    final Path outputPath = writer.resolveOutputPath(projectPath, configuredOutputPath);
    try {
      writer.writeReport(outputPath, htmlContent);
      log.info("Wrote HTML impact report to {}", outputPath.toAbsolutePath().normalize());
      return true;
    } catch (final IOException e) {
      log.error("Failed to write HTML impact report to {}", outputPath.toAbsolutePath().normalize(), e);
      return false;
    }
  }

  private static Path toRelativePath(final Path projectPath, final Path path) {
    return projectPath.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
  }

  private static void configureLogging(final CommandLine cmd) {
    final String level = cmd.hasOption("d") ? "debug" : "info";
    System.setProperty(SIMPLE_LOGGER_DEFAULT_LEVEL, level);
    System.setProperty(SIMPLE_LOGGER_SHOW_DATE_TIME, "true");
    System.setProperty(SIMPLE_LOGGER_DATE_TIME_FORMAT, "yyyy-MM-dd HH:mm:ss.SSS");
    System.setProperty(SIMPLE_LOGGER_SHOW_THREAD_NAME, "false");
  }

  private static Options getOptions() {
    final Options options = new Options();

    // Help option
    options.addOption(Option.builder("h").longOpt("help").desc("Show Help").build());
    options.addOption(
        Option.builder("d")
            .longOpt("debug")
            .desc("Enable debug logging (detailed diagnostics)")
            .build());
    options.addOption(
        Option.builder()
            .longOpt("html-report")
            .hasArg()
            .argName("path-or-directory")
            .desc("Optional output path for static HTML report; directories use impact-report.html")
            .build());

    // Path argument (required)
    options.addOption(
        Option.builder("p")
            .longOpt("project")
            .hasArg()
            .argName("project-path")
            .desc("Path to the (multi-module) git project root")
            .required()
            .build());

    // Config argument (required)
    options.addOption(
        Option.builder("c")
            .longOpt("config")
            .hasArg()
            .argName("config-path")
            .desc("Path to the configuration file")
            .required()
            .build());

    return options;
  }
}
