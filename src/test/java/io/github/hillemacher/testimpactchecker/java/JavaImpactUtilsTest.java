package io.github.hillemacher.testimpactchecker.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.git.GitImpactUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.diff.DiffEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for Java impact analysis utilities.
 */
@ExtendWith(MockitoExtension.class)
class JavaImpactUtilsTest {

  private final Path testConfigPath =
      Paths.get("src", "test", "resources", "configs", "test-config.json");

  private final Path testRepoRootPath = Paths.get("src", "test", "resources", "test-project");

  private JavaParser javaParser = new JavaParser();

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    final ParserConfiguration parserConfiguration = new ParserConfiguration();
    parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    javaParser = new JavaParser(parserConfiguration);
  }

  /**
   * Verifies implemented interfaces are discovered for changed classes.
   */
  @Test
  void testFindImplementedInterfaces() throws IOException {
    final ImpactCheckerConfig impactCheckerTestConfig =
        loadImpactCheckerConfig(this.testConfigPath);

    final Path changedClassPath = Paths.get("foo", "baa", "src", "main", "java", "ClassJSF.java");
    final Set<Path> changedClassPaths = new HashSet<>(Set.of(changedClassPath));

    final JavaImpactUtils javaImpactUtils =
        new JavaImpactUtils(javaParser, impactCheckerTestConfig);
    final Map<Path, Set<String>> implementedInterfaces =
        javaImpactUtils.findImplementedInterfaces(changedClassPaths, testRepoRootPath);
    assertThat(implementedInterfaces).isEqualTo(Map.of(changedClassPath, Set.of("InterfaceJSF")));
  }

  /**
   * Ensures changed class paths are filtered to main Java sources only.
   */
  @Test
  void testFindChangedClassPaths() throws IOException {
    DiffEntry diffEntryMain = mock();
    final Path changedJsf = Paths.get("foo", "baa", "src", "main", "java", "ClassJSF.java");
    when(diffEntryMain.getNewPath()).thenReturn(changedJsf.toString());
    DiffEntry diffEntryTest = mock();
    when(diffEntryTest.getNewPath())
        .thenReturn(Paths.get("foo", "src", "main", "test", "TestClassJSF.java").toString());

    try (MockedStatic<GitImpactUtils> gitImpactUtilsMockedStatic =
        Mockito.mockStatic(GitImpactUtils.class)) {
      final ImpactCheckerConfig impactCheckerTestConfig =
          loadImpactCheckerConfig(this.testConfigPath);
      final Set<Path> mainDirs =
          Set.of(Paths.get(testRepoRootPath.toString(), "foo", "baa", "src", "main", "java"));

      gitImpactUtilsMockedStatic
          .when(() -> GitImpactUtils.getDiffEntries(testRepoRootPath, impactCheckerTestConfig))
          .thenReturn(List.of(diffEntryMain, diffEntryTest));

      JavaImpactUtils javaImpactUtils = new JavaImpactUtils(javaParser, impactCheckerTestConfig);
      final Set<Path> changedClassPaths =
          javaImpactUtils.findChangedClassPaths(mainDirs, testRepoRootPath);

      assertThat(changedClassPaths).containsOnly(changedJsf);
    }
  }

  /**
   * Confirms relevant tests are detected based on annotations and references.
   */
  @Test
  void testFindRelevantTests() throws IOException {
    final Path changedClassPath = Paths.get("foo", "baa", "src", "main", "java", "ClassJSF.java");
    final Map<Path, Set<String>> implementedInterfaces =
        Map.of(changedClassPath, Set.of("InterfaceJSF"));
    final List<Path> testDirs =
        List.of(Paths.get(testRepoRootPath.toString(), "foo", "src", "main", "test"));

    final ImpactCheckerConfig impactCheckerTestConfig =
        loadImpactCheckerConfig(this.testConfigPath);
    JavaImpactUtils javaImpactUtils = new JavaImpactUtils(javaParser, impactCheckerTestConfig);

    final Set<Path> relevantTests = javaImpactUtils.findRelevantTests(List.of(changedClassPath),
        implementedInterfaces, testDirs);
    assertThat(relevantTests).containsOnly(
        Paths.get(testRepoRootPath.toString(), "foo", "src", "main", "test", "TestClassJSF.java"));
  }

  /**
   * Confirms tests without required annotations are ignored.
   */
  @Test
  void testFindRelevantTestsIgnoresMissingAnnotation() throws IOException {
    final ImpactCheckerConfig impactCheckerTestConfig =
        loadImpactCheckerConfig(this.testConfigPath);
    final JavaImpactUtils javaImpactUtils =
        new JavaImpactUtils(javaParser, impactCheckerTestConfig);

    final Path changedClassPath = Paths.get("src", "main", "java", "FooService.java");
    final Path testDir = tempDir.resolve(Paths.get("module", "src", "test", "java"));
    final Path testFile = testDir.resolve("NoAnnotationTest.java");

    writeTestFile(testFile, """
        import org.junit.jupiter.api.Test;
        class NoAnnotationTest {
                private FooService fooService;
                @Test void test() {}
        }
        """);

    final Set<Path> relevantTests =
        javaImpactUtils.findRelevantTests(Set.of(changedClassPath), Map.of(), List.of(testDir));

    assertThat(relevantTests).isEmpty();
  }

  /**
   * Ensures tests are excluded when the changed class is only mocked.
   */
  @Test
  void testFindRelevantTestsExcludesOnlyMockedAndReferencedClass() throws IOException {
    final ImpactCheckerConfig impactCheckerTestConfig =
        loadImpactCheckerConfig(this.testConfigPath);
    final JavaImpactUtils javaImpactUtils =
        new JavaImpactUtils(javaParser, impactCheckerTestConfig);

    final Path changedClassPath = Paths.get("src", "main", "java", "FooService.java");
    final Path testDir = tempDir.resolve(Paths.get("module", "src", "test", "java"));
    final Path testFile = testDir.resolve("MockedAndReferencedTest.java");

    writeTestFile(testFile, """
        import org.junit.jupiter.api.Test;
        import org.springframework.test.context.ContextConfiguration;
        class MockedAndReferencedTest {
                private FooService fooService;
                @Test void test() { mock(FooService.class); }
        }
        """);

    final Set<Path> relevantTests =
        javaImpactUtils.findRelevantTests(Set.of(changedClassPath), Map.of(), List.of(testDir));

    assertThat(relevantTests).isEmpty();
  }

  private ImpactCheckerConfig loadImpactCheckerConfig(Path testConfigPath) throws IOException {
    return new ObjectMapper().readValue(testConfigPath.toFile(), ImpactCheckerConfig.class);
  }

  private void writeTestFile(final Path testFile, final String contents) throws IOException {
    Files.createDirectories(testFile.getParent());
    Files.writeString(testFile, contents, StandardCharsets.UTF_8);
  }
}
