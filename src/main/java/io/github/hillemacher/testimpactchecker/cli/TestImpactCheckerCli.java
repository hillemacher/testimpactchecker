package io.github.hillemacher.testimpactchecker.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hillemacher.testimpactchecker.ImpactDetectionReportData;
import io.github.hillemacher.testimpactchecker.TestImpactChecker;
import io.github.hillemacher.testimpactchecker.cli.output.OutputFormat;
import io.github.hillemacher.testimpactchecker.cli.output.OutputFormatter;
import io.github.hillemacher.testimpactchecker.cli.output.OutputFormatters;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.report.HtmlImpactReportRenderer;
import io.github.hillemacher.testimpactchecker.report.ImpactReport;
import io.github.hillemacher.testimpactchecker.report.ImpactReportMapper;
import io.github.hillemacher.testimpactchecker.report.ImpactReportWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

/** Command-line entry point for running test impact detection against a repository. */
@Slf4j
public class TestImpactCheckerCli {

  private static final String SIMPLE_LOGGER_DEFAULT_LEVEL =
      "org.slf4j.simpleLogger.defaultLogLevel";
  private static final String SIMPLE_LOGGER_SHOW_DATE_TIME = "org.slf4j.simpleLogger.showDateTime";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT =
      "org.slf4j.simpleLogger.dateTimeFormat";
  private static final String SIMPLE_LOGGER_SHOW_THREAD_NAME =
      "org.slf4j.simpleLogger.showThreadName";

  private static final String SEPARATOR = "----------------- ----------------- -----------------";

  /**
   * Entry point for the ChangedClassTestDetectorCLI command-line tool.
   *
   * <p>This method parses command-line arguments, loads configuration from a JSON file, and
   * determines which test classes are relevant based on detected changes in the project. It writes
   * the list of relevant test files using the configured output format(s) and logs important steps
   * and errors.
   *
   * <h2>Command-Line Arguments</h2>
   *
   * <ul>
   *   <li><b>-p &lt;projectPath&gt;</b> : Path to the root of the project to analyze. (Required)
   *   <li><b>-c &lt;configPath&gt;</b> : Path to the JSON configuration file. (Required)
   *   <li><b>--html-report &lt;path-or-directory&gt;</b> : Optional path for a static HTML impact
   *       report. If a directory is given, {@code impact-report.html} is used. This overrides the
   *       optional config field {@code htmlReportOutputPath}.
   *   <li><b>--format &lt;value&gt;</b> : Optional, repeatable. One of {@code human}, {@code json},
   *       {@code gradle-filter}, {@code junit-includes}. Defaults to {@code human} written to
   *       stdout.
   *   <li><b>--format-out &lt;path&gt;</b> : Optional, repeatable. Output path for the matching
   *       {@code --format} entry at the same position; omit to let that entry write to stdout. Only
   *       one format may target stdout per run.
   *   <li><b>-d</b> or <b>--debug</b> : Enables debug logging with detailed diagnostics.
   *   <li><b>-h</b> : Shows help and usage information.
   * </ul>
   *
   * @param args the command-line arguments for the application
   */
  public static void main(final String[] args) {
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter formatter = new HelpFormatter();

    final Options options = getOptions();
    final long startNanos = System.nanoTime();
    boolean success = false;
    boolean stdoutTargetIsHuman = true;
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
      final Path normalizedConfigPath = configPath.toAbsolutePath().normalize();

      final ObjectMapper mapper = new ObjectMapper();
      final ImpactCheckerConfig impactCheckerConfig =
          mapper.readValue(configPath.toFile(), ImpactCheckerConfig.class);
      log.info("Validated config path {}", normalizedConfigPath);

      final List<OutputTarget> outputTargets = parseOutputTargets(cmd);
      stdoutTargetIsHuman = determineStdoutTargetIsHuman(outputTargets);

      final Path normalizedProjectPath = projectPath.toAbsolutePath().normalize();
      if (cmd.hasOption("clear-cache")) {
        TestImpactChecker.clearCache(normalizedProjectPath, impactCheckerConfig);
        log.info(
            "Cleared type-index cache at {}",
            TestImpactChecker.resolveCacheDirectory(normalizedProjectPath, impactCheckerConfig));
      }

      final TestImpactChecker.CacheMode cacheMode = resolveCacheMode(cmd);
      final TestImpactChecker testImpactChecker = new TestImpactChecker();
      log.info("Running impact detection (cache mode: {})", cacheMode);
      final ImpactDetectionReportData impactDetectionReportData =
          testImpactChecker.detectImpactReportData(
              normalizedProjectPath, impactCheckerConfig, cacheMode);
      log.info(
          "Impact detection completed: {} impacted tests found",
          impactDetectionReportData.relevantTestsWithCauses().size());

      boolean htmlReportWrittenSuccessfully = true;
      final Optional<String> configuredHtmlReportOutputPath =
          resolveHtmlReportOutputPath(cmd, impactCheckerConfig);
      if (configuredHtmlReportOutputPath.isPresent()) {
        htmlReportWrittenSuccessfully =
            writeHtmlReport(
                projectPath,
                normalizedConfigPath,
                impactCheckerConfig,
                configuredHtmlReportOutputPath.get(),
                impactDetectionReportData);
      }

      final boolean formatsWritten =
          writeFormattedOutputs(projectPath, outputTargets, impactDetectionReportData);

      success = htmlReportWrittenSuccessfully && formatsWritten;
    } catch (final MissingOptionException e) {
      log.error("Missing required option", e);
      formatter.printHelp("ChangedClassTestDetectorCLI", options, true);
    } catch (final ParseException e) {
      log.error("Error parsing command line", e);
      formatter.printHelp("ChangedClassTestDetectorCLI", options, true);
    } catch (final IllegalArgumentException e) {
      log.error("Invalid argument: {}", e.getMessage());
      formatter.printHelp("ChangedClassTestDetectorCLI", options, true);
    } catch (final IOException e) {
      log.error("Cannot access config file", e);
    }

    if (stdoutTargetIsHuman) {
      System.out.println(SEPARATOR);
      System.out.println();
    }
    log.info(
        "Finished impact analysis {} in {}",
        success ? "with success" : "with problems",
        formatElapsed(System.nanoTime() - startNanos));
  }

  static String formatElapsed(final long elapsedNanos) {
    final long elapsedMillis = elapsedNanos / 1_000_000L;
    if (elapsedMillis < 1000L) {
      return elapsedMillis + " ms";
    }
    return String.format(java.util.Locale.ROOT, "%.3f s", elapsedMillis / 1000.0);
  }

  private static TestImpactChecker.CacheMode resolveCacheMode(final CommandLine cmd) {
    final boolean noCache = cmd.hasOption("no-cache");
    final boolean verify = cmd.hasOption("verify-cache");
    if (noCache && verify) {
      throw new IllegalArgumentException("--no-cache and --verify-cache are mutually exclusive");
    }
    if (verify) {
      return TestImpactChecker.CacheMode.VERIFY;
    }
    if (noCache) {
      return TestImpactChecker.CacheMode.DISABLED;
    }
    return TestImpactChecker.CacheMode.ENABLED;
  }

  private static Optional<String> resolveHtmlReportOutputPath(
      @NonNull final CommandLine cmd, @NonNull final ImpactCheckerConfig impactCheckerConfig) {
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
      @NonNull final Path configPath,
      @NonNull final ImpactCheckerConfig impactCheckerConfig,
      @NonNull final String configuredOutputPath,
      @NonNull final ImpactDetectionReportData impactDetectionReportData) {
    final ImpactReportMapper mapper = new ImpactReportMapper();
    final HtmlImpactReportRenderer renderer = new HtmlImpactReportRenderer();
    final ImpactReportWriter writer = new ImpactReportWriter();

    final ImpactReport report =
        mapper.toImpactReport(
            projectPath,
            configPath,
            impactCheckerConfig,
            ZoneId.systemDefault(),
            impactDetectionReportData.relevantTestsWithCauses(),
            impactDetectionReportData.impactedTypeToCauses());
    final String htmlContent = renderer.render(report);
    final Path outputPath = writer.resolveOutputPath(projectPath, configuredOutputPath);
    try {
      writer.writeReport(outputPath, htmlContent);
      log.info("Wrote HTML impact report to {}", outputPath.toAbsolutePath().normalize());
      return true;
    } catch (final IOException e) {
      log.error(
          "Failed to write HTML impact report to {}", outputPath.toAbsolutePath().normalize(), e);
      return false;
    }
  }

  /**
   * Parses the {@code --format} / {@code --format-out} option pairs. When neither is given, a
   * single HUMAN-to-stdout target is returned so default behavior is preserved.
   */
  static List<OutputTarget> parseOutputTargets(final CommandLine cmd) {
    final String[] rawFormats = cmd.getOptionValues("format");
    final String[] rawOuts = cmd.getOptionValues("format-out");
    final List<String> formats = rawFormats == null ? List.of() : List.of(rawFormats);
    final List<String> outs = rawOuts == null ? List.of() : List.of(rawOuts);

    if (formats.isEmpty() && outs.isEmpty()) {
      return List.of(new OutputTarget(OutputFormat.HUMAN, null));
    }

    if (!outs.isEmpty() && outs.size() != formats.size()) {
      throw new IllegalArgumentException(
          "--format-out must appear the same number of times as --format (got "
              + formats.size()
              + " formats and "
              + outs.size()
              + " outputs)");
    }

    final List<OutputTarget> targets = new ArrayList<>();
    int stdoutCount = 0;
    for (int i = 0; i < formats.size(); i++) {
      final OutputFormat format = OutputFormat.fromCli(formats.get(i));
      final String outPath = outs.isEmpty() ? null : outs.get(i);
      final String effectiveOut = (outPath == null || outPath.isBlank()) ? null : outPath;
      if (effectiveOut == null) {
        stdoutCount++;
      }
      targets.add(new OutputTarget(format, effectiveOut));
    }

    if (stdoutCount > 1) {
      throw new IllegalArgumentException(
          "Only one --format may target stdout (omit --format-out for at most one format)");
    }
    return targets;
  }

  private static boolean determineStdoutTargetIsHuman(final List<OutputTarget> targets) {
    return targets.stream()
        .filter(target -> target.outputPath() == null)
        .findFirst()
        .map(target -> target.format() == OutputFormat.HUMAN)
        .orElse(false);
  }

  private static boolean writeFormattedOutputs(
      final Path projectPath,
      final List<OutputTarget> targets,
      final ImpactDetectionReportData data) {
    boolean allSucceeded = true;
    for (final OutputTarget target : targets) {
      final OutputFormatter formatter = OutputFormatters.forFormat(target.format());
      if (target.outputPath() == null) {
        final Writer writer = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        try {
          formatter.write(projectPath, data, writer);
          writer.flush();
        } catch (final IOException e) {
          log.error("Failed to write {} output to stdout", target.format().cliName(), e);
          allSucceeded = false;
        }
      } else {
        final Path outputPath = resolveFormatOutputPath(projectPath, target.outputPath());
        try {
          if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
          }
          try (final BufferedWriter writer =
              Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            formatter.write(projectPath, data, writer);
          }
          log.info(
              "Wrote {} output to {}",
              target.format().cliName(),
              outputPath.toAbsolutePath().normalize());
        } catch (final IOException e) {
          log.error(
              "Failed to write {} output to {}",
              target.format().cliName(),
              outputPath.toAbsolutePath().normalize(),
              e);
          allSucceeded = false;
        }
      }
    }
    return allSucceeded;
  }

  private static Path resolveFormatOutputPath(
      final Path projectPath, final String configuredOutputPath) {
    final Path candidate = Paths.get(configuredOutputPath);
    if (candidate.isAbsolute()) {
      return candidate;
    }
    return projectPath.toAbsolutePath().normalize().resolve(candidate).normalize();
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
    options.addOption(
        Option.builder()
            .longOpt("format")
            .hasArg()
            .argName("value")
            .desc(
                "Output format: human (default), json, gradle-filter, junit-includes. "
                    + "Repeatable; pair with --format-out at the same position to write to a file.")
            .build());
    options.addOption(
        Option.builder()
            .longOpt("format-out")
            .hasArg()
            .argName("path")
            .desc(
                "Output path for the matching --format entry. Omit to write that format to stdout."
                    + " Only one format may target stdout per run.")
            .build());
    options.addOption(
        Option.builder()
            .longOpt("no-cache")
            .desc("Disable the persistent main-source index cache for this run.")
            .build());
    options.addOption(
        Option.builder()
            .longOpt("clear-cache")
            .desc("Delete the persistent cache directory before running analysis.")
            .build());
    options.addOption(
        Option.builder()
            .longOpt("verify-cache")
            .desc(
                "Run analysis twice (uncached then cached) and assert parity; fails on mismatch."
                    + " Useful as a one-off correctness check in CI.")
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

  /** One resolved {@code --format} entry paired with its optional destination path. */
  record OutputTarget(OutputFormat format, String outputPath) {}
}
