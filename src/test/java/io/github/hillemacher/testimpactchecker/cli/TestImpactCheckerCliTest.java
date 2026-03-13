package io.github.hillemacher.testimpactchecker.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.hillemacher.testimpactchecker.ImpactDetectionReportData;
import io.github.hillemacher.testimpactchecker.TestImpactChecker;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

/**
 * Tests CLI argument handling, console output stability, and HTML report output behavior.
 */
class TestImpactCheckerCliTest {

  private static final Path CLI_PREVIEW_REPORT_PATH =
      Path.of("build", "test-artifacts", "cli-preview", "impact-report.html");
  private static final String SIMPLE_LOGGER_DEFAULT_LEVEL =
      "org.slf4j.simpleLogger.defaultLogLevel";
  private static final String SIMPLE_LOGGER_SHOW_DATE_TIME =
      "org.slf4j.simpleLogger.showDateTime";
  private static final String SIMPLE_LOGGER_DATE_TIME_FORMAT =
      "org.slf4j.simpleLogger.dateTimeFormat";
  private static final String SIMPLE_LOGGER_SHOW_THREAD_NAME =
      "org.slf4j.simpleLogger.showThreadName";

  @TempDir
  Path tempDir;

  private final Map<String, String> originalSystemProperties = new HashMap<>();

  @BeforeEach
  void storeLoggingSystemProperties() {
    for (final String key : loggingPropertyKeys()) {
      originalSystemProperties.put(key, System.getProperty(key));
    }
  }

  @AfterEach
  void restoreLoggingSystemProperties() {
    for (final String key : loggingPropertyKeys()) {
      final String originalValue = originalSystemProperties.get(key);
      if (originalValue == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, originalValue);
      }
    }
    originalSystemProperties.clear();
  }

  /**
   * Verifies impacted tests are printed grouped by test path and sorted deterministically.
   */
  @Test
  void testMainPrintsGroupedImpactCauses() throws IOException {
    final Path projectPath = tempDir.resolve("project-default");
    final Path configPath = writeConfig(projectPath);
    final String output = executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString()});

    assertThat(output).contains("Relevant tests and impact causes:");
    assertThat(output).contains("module/src/test/java/a/TestA.java");
    assertThat(output).contains("  caused by: AClass, CClass");
    assertThat(output).contains("module/src/test/java/b/TestB.java");

    final int indexA = output.indexOf("module/src/test/java/a/TestA.java");
    final int indexB = output.indexOf("module/src/test/java/b/TestB.java");
    assertThat(indexA).isLessThan(indexB);
  }

  /**
   * Ensures default execution configures INFO-level simple logger properties.
   */
  @Test
  void testMainSetsInfoLogLevelByDefault() throws IOException {
    final Path projectPath = tempDir.resolve("project-info");
    final Path configPath = writeConfig(projectPath);

    executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString()});

    assertThat(System.getProperty(SIMPLE_LOGGER_DEFAULT_LEVEL)).isEqualTo("info");
    assertThat(System.getProperty(SIMPLE_LOGGER_SHOW_DATE_TIME)).isEqualTo("true");
    assertThat(System.getProperty(SIMPLE_LOGGER_DATE_TIME_FORMAT))
        .isEqualTo("yyyy-MM-dd HH:mm:ss.SSS");
    assertThat(System.getProperty(SIMPLE_LOGGER_SHOW_THREAD_NAME)).isEqualTo("false");
  }

  /**
   * Ensures the debug flag switches logging configuration to DEBUG level.
   */
  @Test
  void testMainSetsDebugLogLevelWhenDebugFlagPresent() throws IOException {
    final Path projectPath = tempDir.resolve("project-debug");
    final Path configPath = writeConfig(projectPath);

    executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString(), "--debug"});

    assertThat(System.getProperty(SIMPLE_LOGGER_DEFAULT_LEVEL)).isEqualTo("debug");
  }

  /**
   * Verifies enabling debug logging does not change the structured stdout report content.
   */
  @Test
  void testMainDebugFlagKeepsReportOutputStable() throws IOException {
    final Path projectPath = tempDir.resolve("project-stable");
    final Path configPath = writeConfig(projectPath);

    final String defaultOutput = executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString()});
    final String debugOutput = executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString(), "--debug"});

    assertThat(debugOutput).isEqualTo(defaultOutput);
  }

  /**
   * Confirms HTML output is written to an explicit file path passed via CLI.
   */
  @Test
  void testMainWritesHtmlReportToExplicitFilePath() throws IOException {
    final Path projectPath = tempDir.resolve("project-html-file");
    final Path configPath = writeConfig(projectPath);
    final Path htmlPath = projectPath.resolve("reports/custom-report.html");

    executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString(), "--html-report",
            "reports/custom-report.html"});

    assertThat(htmlPath).exists();
    final String html = Files.readString(htmlPath, StandardCharsets.UTF_8);
    assertThat(html).contains("Test Impact Report");
    assertThat(html).contains("Impacted tests and causes");
    assertThat(html).contains("module/src/test/java/a/TestA.java");
  }

  /**
   * Confirms directory targets resolve to the default report file name.
   */
  @Test
  void testMainWritesHtmlReportToDirectoryPathWithDefaultFileName() throws IOException {
    final Path projectPath = tempDir.resolve("project-html-dir");
    final Path configPath = writeConfig(projectPath);
    final Path htmlPath = projectPath.resolve("reports/impact-report.html");

    executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString(), "--html-report",
            "reports"});

    assertThat(htmlPath).exists();
  }

  /**
   * Verifies adding HTML output does not alter the existing stdout report format.
   */
  @Test
  void testMainHtmlFlagKeepsConsoleReportStable() throws IOException {
    final Path projectPath = tempDir.resolve("project-html-stable");
    final Path configPath = writeConfig(projectPath);

    final String defaultOutput = executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString()});
    final String htmlOutput = executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString(), "--html-report",
            "reports"});

    assertThat(htmlOutput).isEqualTo(defaultOutput);
  }

  /**
   * Verifies HTML output is produced from config when the CLI report flag is absent.
   */
  @Test
  void testMainWritesHtmlReportFromConfigWhenCliFlagIsMissing() throws IOException {
    final Path projectPath = tempDir.resolve("project-config-html");
    final Path configPath = writeConfig(projectPath, "reports/from-config");
    final Path htmlPath = projectPath.resolve("reports/from-config/impact-report.html");

    executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString()});

    assertThat(htmlPath).exists();
    final String html = Files.readString(htmlPath, StandardCharsets.UTF_8);
    assertThat(html).contains("Run metadata");
    assertThat(html).contains("Base ref");
    assertThat(html).contains("develop");
    assertThat(html).contains("Target ref");
    assertThat(html).contains("HEAD");
    assertThat(html).contains("Annotations");
    assertThat(html).contains("ContextConfiguration, IntegrationTest");
    assertThat(html).contains("Analysis mode");
    assertThat(html).contains("TRANSITIVE");
    assertThat(html).contains("Max propagation depth");
    assertThat(html).contains(">3<");
    assertThat(html).contains("Mock policy");
    assertThat(html).contains("FILTER_MOCKED_PATHS");
    assertThat(html).contains(configPath.toAbsolutePath().normalize().toString());
  }

  /**
   * Ensures CLI report path overrides the optional report path configured in JSON.
   */
  @Test
  void testMainCliHtmlFlagOverridesConfigHtmlPath() throws IOException {
    final Path projectPath = tempDir.resolve("project-config-override");
    final Path configPath = writeConfig(projectPath, "reports/from-config");
    final Path configHtmlPath = projectPath.resolve("reports/from-config/impact-report.html");
    final Path cliHtmlPath = projectPath.resolve("reports/from-cli/impact-report.html");

    executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString(), "--html-report",
            "reports/from-cli"});

    assertThat(cliHtmlPath).exists();
    assertThat(configHtmlPath).doesNotExist();
  }

  /**
   * Creates a deterministic preview report under build/ so the generated HTML can be inspected manually.
   */
  @Test
  void testMainWritesPreviewHtmlReportToBuildDirectory() throws IOException {
    final Path projectPath = tempDir.resolve("project-build-preview");
    final Path configPath = writeConfig(projectPath);
    final Path previewReportPath = CLI_PREVIEW_REPORT_PATH.toAbsolutePath().normalize();

    Files.createDirectories(previewReportPath.getParent());
    Files.deleteIfExists(previewReportPath);

    executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString(), "--html-report",
            previewReportPath.toString()});

    assertThat(previewReportPath).exists();
    final String html = Files.readString(previewReportPath, StandardCharsets.UTF_8);
    assertThat(html).contains("Test Impact Report");
    assertThat(html).contains("Run metadata");
    assertThat(html).contains("Impact graph");
  }

  private String executeCliAndCaptureStdout(final Path projectPath, final String[] args) {
    final Path testB = projectPath.resolve("module/src/test/java/b/TestB.java");
    final Path testA = projectPath.resolve("module/src/test/java/a/TestA.java");
    final Map<Path, Set<String>> impacts = new LinkedHashMap<>();
    impacts.put(testB, Set.of("BClass"));
    impacts.put(testA, Set.of("CClass", "AClass"));
    final Map<String, Set<String>> impactedTypeToCauses = Map.of(
        "AClass", Set.of("AClass"),
        "CClass", Set.of("CClass"),
        "FacadeType", Set.of("AClass", "CClass"));

    final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    final PrintStream originalOut = System.out;
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    try (MockedConstruction<TestImpactChecker> construction =
        Mockito.mockConstruction(TestImpactChecker.class, (mock, context) -> {
          when(mock.detectImpactReportData(any(Path.class), any(ImpactCheckerConfig.class)))
              .thenReturn(new ImpactDetectionReportData(impacts, impactedTypeToCauses));
        })) {
      TestImpactCheckerCli.main(args);
      assertThat(construction.constructed()).hasSize(1);
      return stdout.toString(StandardCharsets.UTF_8);
    } finally {
      System.setOut(originalOut);
    }
  }

  private Path writeConfig(final Path projectPath) throws IOException {
    return writeConfig(projectPath, null);
  }

  private Path writeConfig(final Path projectPath, final String htmlReportOutputPath)
      throws IOException {
    Files.createDirectories(projectPath);
    final Path configPath = tempDir.resolve(projectPath.getFileName() + "-config.json");
    final String optionalHtmlPath = htmlReportOutputPath == null ? "" :
        ",\n  \"htmlReportOutputPath\": \"" + htmlReportOutputPath + "\"";
    Files.writeString(configPath, """
        {
          "annotations": ["ContextConfiguration", "IntegrationTest"],
          "baseRef": "develop",
          "targetRef": "HEAD",
          "analysisMode": "TRANSITIVE",
          "maxPropagationDepth": 3,
          "mockPolicy": "FILTER_MOCKED_PATHS"%s
        }
        """.formatted(optionalHtmlPath), StandardCharsets.UTF_8);
    return configPath;
  }

  private List<String> loggingPropertyKeys() {
    return List.of(
        SIMPLE_LOGGER_DEFAULT_LEVEL,
        SIMPLE_LOGGER_SHOW_DATE_TIME,
        SIMPLE_LOGGER_DATE_TIME_FORMAT,
        SIMPLE_LOGGER_SHOW_THREAD_NAME);
  }
}
