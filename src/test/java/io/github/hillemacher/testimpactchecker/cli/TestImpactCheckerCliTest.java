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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

class TestImpactCheckerCliTest {

  @TempDir
  Path tempDir;

  @Test
  void testMainPrintsGroupedImpactCauses() throws IOException {
    final Path projectPath = tempDir.resolve("project");
    Files.createDirectories(projectPath);
    final Path configPath = tempDir.resolve("config.json");
    Files.writeString(configPath, """
        {
          "annotations": ["ContextConfiguration"],
          "baseRef": "develop",
          "targetRef": "HEAD"
        }
        """, StandardCharsets.UTF_8);

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
      TestImpactCheckerCli.main(
          new String[] {"-p", projectPath.toString(), "-c", configPath.toString()});

      assertThat(construction.constructed()).hasSize(1);
      final String output = stdout.toString(StandardCharsets.UTF_8);
      assertThat(output).contains("Relevant tests and impact causes:");
      assertThat(output).contains("module/src/test/java/a/TestA.java");
      assertThat(output).contains("  caused by: AClass, CClass");
      assertThat(output).contains("module/src/test/java/b/TestB.java");

      final int indexA = output.indexOf("module/src/test/java/a/TestA.java");
      final int indexB = output.indexOf("module/src/test/java/b/TestB.java");
      assertThat(indexA).isLessThan(indexB);
    } finally {
      System.setOut(originalOut);
    }
  }
}
