package io.github.hillemacher.testimpactchecker.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests for the Gradle plugin using TestKit against a small fixture project that lives
 * under {@code src/integrationTest/resources/fixture-project/}. Each test copies the fixture into a
 * fresh temp dir, initializes a git repo via CLI, makes a change, and runs the plugin's tasks.
 */
class TestImpactCheckerPluginIT {

  @TempDir Path workDir;

  private Path fixtureDir;

  @BeforeEach
  void setUp() throws Exception {
    fixtureDir = workDir.resolve("fixture");
    copyFixture(locateFixture(), fixtureDir);
    runCommand(fixtureDir, "git", "init", "-b", "main");
    runCommand(fixtureDir, "git", "config", "user.email", "it@example.com");
    runCommand(fixtureDir, "git", "config", "user.name", "IT");
    runCommand(fixtureDir, "git", "add", "-A");
    runCommand(fixtureDir, "git", "commit", "-m", "initial");
  }

  @Test
  void testImpactCheckWritesJsonArtifactWithImpactedFqcn() throws Exception {
    // Change Alpha.java only (non-behavioural so AlphaTest stays green);
    // BetaTest should not be impacted.
    makeNonBreakingChangeToAlpha();

    final BuildResult result = newRunner().withArguments("testImpactCheck", "--stacktrace").build();

    assertThat(result.task(":testImpactCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

    final Path json = fixtureDir.resolve("build/test-impact/impact.json");
    assertThat(json).exists();

    final JsonNode root = new ObjectMapper().readTree(json.toFile());
    assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);
    final JsonNode tests = root.get("impactedTests");
    assertThat(tests.isArray()).isTrue();
    assertThat(collectFqcns(tests)).containsExactly("com.example.AlphaTest");
  }

  @Test
  void applyToFiltersTestTaskToOnlyImpactedClasses() throws Exception {
    makeNonBreakingChangeToAlpha();

    final BuildResult result = newRunner().withArguments("test", "--stacktrace").build();

    assertThat(result.task(":testImpactCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.task(":test").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

    // Gradle writes one TEST-<fqcn>.xml per executed test class. AlphaTest should be present;
    // BetaTest should not.
    final Path resultsDir = fixtureDir.resolve("build/test-results/test");
    assertThat(resultsDir.resolve("TEST-com.example.AlphaTest.xml")).exists();
    assertThat(resultsDir.resolve("TEST-com.example.BetaTest.xml")).doesNotExist();
  }

  @Test
  void emptyImpactedSetSkipsAllTestsWithoutFailing() throws Exception {
    // No source change after initial commit — baseRef=HEAD~1 still resolves (to initial).
    // Make an empty commit so HEAD..HEAD~1 diff is empty.
    runCommand(fixtureDir, "git", "commit", "--allow-empty", "-m", "no-op");

    final BuildResult result = newRunner().withArguments("test", "--stacktrace").build();

    assertThat(result.task(":testImpactCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.task(":test").getOutcome())
        .isIn(TaskOutcome.SUCCESS, TaskOutcome.NO_SOURCE, TaskOutcome.UP_TO_DATE);
    assertThat(result.getOutput()).contains("no impacted tests detected");
  }

  private void makeNonBreakingChangeToAlpha() throws Exception {
    // Add an unused helper method — changes the file's content hash (so the Git diff contains it)
    // while preserving the public behavior AlphaTest asserts against.
    final Path alpha = fixtureDir.resolve("src/main/java/com/example/Alpha.java");
    final String original = Files.readString(alpha, StandardCharsets.UTF_8);
    final String patched =
        original.replace(
            "public String greet() {",
            "public int version() { return 1; }\n\n  public String greet() {");
    Files.writeString(alpha, patched, StandardCharsets.UTF_8);
    runCommand(fixtureDir, "git", "add", "-A");
    runCommand(fixtureDir, "git", "commit", "-m", "add version() to Alpha");
  }

  private GradleRunner newRunner() {
    return GradleRunner.create()
        .withProjectDir(fixtureDir.toFile())
        .withPluginClasspath()
        .forwardOutput();
  }

  private static Path locateFixture() throws IOException {
    final URL resource =
        TestImpactCheckerPluginIT.class
            .getClassLoader()
            .getResource("fixture-project/settings.gradle");
    if (resource == null) {
      throw new IllegalStateException("fixture-project resources not on the classpath");
    }
    try {
      return Path.of(resource.toURI()).getParent();
    } catch (final Exception e) {
      throw new IOException(e);
    }
  }

  private static void copyFixture(final Path source, final Path target) throws IOException {
    Files.createDirectories(target);
    try (Stream<Path> paths = Files.walk(source)) {
      paths.forEach(
          p -> {
            final Path relative = source.relativize(p);
            final Path dest = target.resolve(relative.toString());
            try {
              if (Files.isDirectory(p)) {
                Files.createDirectories(dest);
              } else {
                Files.createDirectories(dest.getParent());
                Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
              }
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }
          });
    }
  }

  private static void runCommand(final Path workingDir, final String... command) throws Exception {
    final ProcessBuilder pb =
        new ProcessBuilder(command).directory(workingDir.toFile()).redirectErrorStream(true);
    final Process process = pb.start();
    final String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    final int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException(
          "Command " + String.join(" ", command) + " failed (exit " + exit + "):\n" + output);
    }
  }

  private static java.util.List<String> collectFqcns(final JsonNode impactedTests) {
    return java.util.stream.StreamSupport.stream(impactedTests.spliterator(), false)
        .map(node -> node.get("fqcn").asText())
        .sorted(Comparator.naturalOrder())
        .toList();
  }
}
