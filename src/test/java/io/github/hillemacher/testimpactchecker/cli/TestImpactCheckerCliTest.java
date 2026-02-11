package io.github.hillemacher.testimpactchecker.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

class TestImpactCheckerCliTest {

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

  @Test
  void testMainSetsDebugLogLevelWhenDebugFlagPresent() throws IOException {
    final Path projectPath = tempDir.resolve("project-debug");
    final Path configPath = writeConfig(projectPath);

    executeCliAndCaptureStdout(projectPath,
        new String[] {"-p", projectPath.toString(), "-c", configPath.toString(), "--debug"});

    assertThat(System.getProperty(SIMPLE_LOGGER_DEFAULT_LEVEL)).isEqualTo("debug");
  }

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

  private String executeCliAndCaptureStdout(final Path projectPath, final String[] args) {
    final Path testB = projectPath.resolve("module/src/test/java/b/TestB.java");
    final Path testA = projectPath.resolve("module/src/test/java/a/TestA.java");
    final Map<Path, Set<String>> impacts = new LinkedHashMap<>();
    impacts.put(testB, Set.of("BClass"));
    impacts.put(testA, Set.of("CClass", "AClass"));

    final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    final PrintStream originalOut = System.out;
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    try (MockedConstruction<TestImpactChecker> construction =
        Mockito.mockConstruction(TestImpactChecker.class, (mock, context) -> {
          when(mock.detectImpactWithCauses(any(Path.class), any(ImpactCheckerConfig.class)))
              .thenReturn(impacts);
        })) {
      TestImpactCheckerCli.main(args);
      assertThat(construction.constructed()).hasSize(1);
      return stdout.toString(StandardCharsets.UTF_8);
    } finally {
      System.setOut(originalOut);
    }
  }

  private Path writeConfig(final Path projectPath) throws IOException {
    Files.createDirectories(projectPath);
    final Path configPath = tempDir.resolve(projectPath.getFileName() + "-config.json");
    Files.writeString(configPath, """
        {
          "annotations": ["ContextConfiguration"],
          "baseRef": "develop",
          "targetRef": "HEAD"
        }
        """, StandardCharsets.UTF_8);
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
