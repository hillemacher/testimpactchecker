package io.github.hillemacher.testimpactchecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import io.github.hillemacher.testimpactchecker.git.GitImpactUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.diff.DiffEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** Tests transitive impact propagation depth and mock-path filtering behavior. */
class TransitiveImpactCheckerTest {

  @TempDir Path tempDir;

  /** Verifies a downstream test is included when transitive propagation reaches it within depth. */
  @Test
  void testTransitiveImpactIncludesDownstreamTestAtDepthTwo() throws IOException {
    final Path repoRoot = createProjectSkeleton(tempDir);
    final Path testFile =
        writeDownstreamTest(repoRoot, "DownstreamIntegrationTest", "private D service;");
    writeMainChain(repoRoot);

    final ImpactCheckerConfig config = createTransitiveConfig(2, MockPolicy.FILTER_MOCKED_PATHS);
    final DiffEntry changedA = mockDiffEntry("module/src/main/java/A.java");

    try (MockedStatic<GitImpactUtils> gitImpactUtilsMockedStatic =
        Mockito.mockStatic(GitImpactUtils.class)) {
      gitImpactUtilsMockedStatic
          .when(() -> GitImpactUtils.getDiffEntries(repoRoot, config))
          .thenReturn(List.of(changedA));

      final Map<Path, Set<String>> impacted =
          new TestImpactChecker().detectImpactWithCauses(repoRoot, config);

      assertThat(impacted).containsOnlyKeys(testFile);
      assertThat(impacted.get(testFile)).containsExactly("A");
    }
  }

  /**
   * Verifies transitive propagation does not include downstream tests beyond configured max depth.
   */
  @Test
  void testTransitiveImpactRespectsMaxDepth() throws IOException {
    final Path repoRoot = createProjectSkeleton(tempDir);
    writeDownstreamTest(repoRoot, "DepthLimitedTest", "private D service;");
    writeMainChain(repoRoot);

    final ImpactCheckerConfig config = createTransitiveConfig(1, MockPolicy.FILTER_MOCKED_PATHS);
    final DiffEntry changedA = mockDiffEntry("module/src/main/java/A.java");

    try (MockedStatic<GitImpactUtils> gitImpactUtilsMockedStatic =
        Mockito.mockStatic(GitImpactUtils.class)) {
      gitImpactUtilsMockedStatic
          .when(() -> GitImpactUtils.getDiffEntries(repoRoot, config))
          .thenReturn(List.of(changedA));

      final Map<Path, Set<String>> impacted =
          new TestImpactChecker().detectImpactWithCauses(repoRoot, config);

      assertThat(impacted).isEmpty();
    }
  }

  /** Verifies a cause is excluded when every witness path is blocked by mocked intermediates. */
  @Test
  void testTransitiveImpactExcludesFullyMockedPaths() throws IOException {
    final Path repoRoot = createProjectSkeleton(tempDir);
    writeDownstreamTest(repoRoot, "MockedPathTest", "private D service; @Mock private B mockedB;");
    writeMainChain(repoRoot);

    final ImpactCheckerConfig config = createTransitiveConfig(2, MockPolicy.FILTER_MOCKED_PATHS);
    final DiffEntry changedA = mockDiffEntry("module/src/main/java/A.java");

    try (MockedStatic<GitImpactUtils> gitImpactUtilsMockedStatic =
        Mockito.mockStatic(GitImpactUtils.class)) {
      gitImpactUtilsMockedStatic
          .when(() -> GitImpactUtils.getDiffEntries(repoRoot, config))
          .thenReturn(List.of(changedA));

      final Map<Path, Set<String>> impacted =
          new TestImpactChecker().detectImpactWithCauses(repoRoot, config);

      assertThat(impacted).isEmpty();
    }
  }

  /** Verifies unrelated mocks do not suppress a valid transitive cause. */
  @Test
  void testTransitiveImpactKeepsCauseWhenMockIsUnrelated() throws IOException {
    final Path repoRoot = createProjectSkeleton(tempDir);
    final Path testFile =
        writeDownstreamTest(
            repoRoot, "UnrelatedMockTest", "private D service; @Mock private X mockedX;");
    writeMainChain(repoRoot);

    final ImpactCheckerConfig config = createTransitiveConfig(2, MockPolicy.FILTER_MOCKED_PATHS);
    final DiffEntry changedA = mockDiffEntry("module/src/main/java/A.java");

    try (MockedStatic<GitImpactUtils> gitImpactUtilsMockedStatic =
        Mockito.mockStatic(GitImpactUtils.class)) {
      gitImpactUtilsMockedStatic
          .when(() -> GitImpactUtils.getDiffEntries(repoRoot, config))
          .thenReturn(List.of(changedA));

      final Map<Path, Set<String>> impacted =
          new TestImpactChecker().detectImpactWithCauses(repoRoot, config);

      assertThat(impacted).containsOnlyKeys(testFile);
      assertThat(impacted.get(testFile)).containsExactly("A");
    }
  }

  private Path createProjectSkeleton(final Path root) throws IOException {
    final Path repoRoot = root.resolve("repo");
    Files.createDirectories(repoRoot.resolve("module/src/main/java"));
    Files.createDirectories(repoRoot.resolve("module/src/test/java"));
    return repoRoot;
  }

  private void writeMainChain(final Path repoRoot) throws IOException {
    writeJava(repoRoot.resolve("module/src/main/java/A.java"), "public class A implements B {}");
    writeJava(repoRoot.resolve("module/src/main/java/B.java"), "public interface B {}");
    writeJava(
        repoRoot.resolve("module/src/main/java/C.java"),
        "public class C { private B dependency; }");
    writeJava(
        repoRoot.resolve("module/src/main/java/D.java"),
        "public class D { private C dependency; }");
    writeJava(repoRoot.resolve("module/src/main/java/X.java"), "public class X {}");
  }

  private Path writeDownstreamTest(final Path repoRoot, final String className, final String body)
      throws IOException {
    final Path file = repoRoot.resolve("module/src/test/java/" + className + ".java");
    writeJava(
        file,
        """
        import org.mockito.Mock;
        @ContextConfiguration
        class %s {
          %s
        }
        """
            .formatted(className, body));
    return file;
  }

  private void writeJava(final Path path, final String contents) throws IOException {
    Files.writeString(path, contents, StandardCharsets.UTF_8);
  }

  private DiffEntry mockDiffEntry(final String newPath) {
    final DiffEntry diffEntry = mock();
    when(diffEntry.getNewPath()).thenReturn(newPath);
    return diffEntry;
  }

  private ImpactCheckerConfig createTransitiveConfig(final int depth, final MockPolicy mockPolicy) {
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setAnnotations(List.of("ContextConfiguration"));
    config.setBaseRef("HEAD~1");
    config.setTargetRef("HEAD");
    config.setAnalysisMode(AnalysisMode.TRANSITIVE);
    config.setMaxPropagationDepth(depth);
    config.setMockPolicy(mockPolicy);
    return config;
  }
}
