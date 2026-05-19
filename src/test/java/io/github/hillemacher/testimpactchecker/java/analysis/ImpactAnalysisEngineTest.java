package io.github.hillemacher.testimpactchecker.java.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ImpactAnalysisEngine} pipeline orchestration. */
@ExtendWith(MockitoExtension.class)
class ImpactAnalysisEngineTest {

  @TempDir Path tempDir;

  @Mock ChangedClassLocator changedClassLocator;
  @Mock ChangedTypeSeedResolver changedTypeSeedResolver;
  @Mock MainSourceIndexBuilder mainSourceIndexBuilder;
  @Mock TransitiveImpactPropagator transitiveImpactPropagator;
  @Mock TestTypeUsageExtractor testTypeUsageExtractor;
  @Mock TestMockUsageExtractor testMockUsageExtractor;
  @Mock TestImpactEvaluator testImpactEvaluator;

  /** When no changed classes are found, the engine must short-circuit with an empty result. */
  @Test
  void noChangedClassesReturnsEmptyResult() throws IOException {
    final Path mainDir = createDir(tempDir.resolve("src/main/java"));
    final Path testDir = createDir(tempDir.resolve("src/test/java"));
    when(changedClassLocator.findChangedClassPaths(Set.of(mainDir), tempDir)).thenReturn(Set.of());

    final ImpactAnalysisResult result =
        newEngine(directConfig()).analyzeImpact(tempDir, Set.of(mainDir), Set.of(testDir));

    assertThat(result.relevantTestsWithCauses()).isEmpty();
    assertThat(result.propagationResult().impactedTypeToCauses()).isEmpty();
    assertThat(result.testFileToFqcn()).isEmpty();
    verify(changedTypeSeedResolver, never()).resolve(any(), any());
    verify(testImpactEvaluator, never()).evaluateCauses(any(), any(), any(), any(), any(), any());
  }

  /** Direct mode includes a test that references a changed class with the cause attached. */
  @Test
  void directModeIncludesTestThatReferencesChangedClass() throws IOException {
    final Path mainDir = createDir(tempDir.resolve("src/main/java"));
    final Path testDir = createDir(tempDir.resolve("src/test/java"));
    final Path testFile = writeJavaFile(testDir, "ATest.java", "public class ATest {}");
    final Path changedFile = mainDir.resolve("A.java");

    when(changedClassLocator.findChangedClassPaths(Set.of(mainDir), tempDir))
        .thenReturn(Set.of(changedFile));
    when(changedTypeSeedResolver.resolve(Set.of(changedFile), tempDir))
        .thenReturn(new ChangedTypeSeedData(Map.of(), Map.of("A", Set.of("A")), Set.of("A")));
    when(testTypeUsageExtractor.extract(any(CompilationUnit.class)))
        .thenReturn(new TestTypeUsage(true, Set.of("A"), "com.example.ATest"));
    when(testMockUsageExtractor.extractMockedTypes(any(CompilationUnit.class)))
        .thenReturn(Set.of());
    when(testImpactEvaluator.evaluateCauses(
            eq(Set.of("A")), eq(Set.of()), eq(Set.of("A")), any(), any(), eq(MockPolicy.CURRENT)))
        .thenReturn(Set.of("A"));

    final ImpactAnalysisResult result =
        newEngine(directConfig()).analyzeImpact(tempDir, Set.of(mainDir), Set.of(testDir));

    assertThat(result.relevantTestsWithCauses()).containsOnlyKeys(testFile);
    assertThat(result.relevantTestsWithCauses().get(testFile)).containsExactly("A");
    assertThat(result.testFileToFqcn()).containsEntry(testFile, "com.example.ATest");
  }

  /** Tests without the required impact annotation must be skipped before cause evaluation. */
  @Test
  void directModeExcludesTestWithoutRequiredAnnotation() throws IOException {
    final Path mainDir = createDir(tempDir.resolve("src/main/java"));
    final Path testDir = createDir(tempDir.resolve("src/test/java"));
    writeJavaFile(testDir, "PlainTest.java", "public class PlainTest {}");
    final Path changedFile = mainDir.resolve("A.java");

    when(changedClassLocator.findChangedClassPaths(Set.of(mainDir), tempDir))
        .thenReturn(Set.of(changedFile));
    when(changedTypeSeedResolver.resolve(Set.of(changedFile), tempDir))
        .thenReturn(new ChangedTypeSeedData(Map.of(), Map.of("A", Set.of("A")), Set.of("A")));
    when(testTypeUsageExtractor.extract(any(CompilationUnit.class)))
        .thenReturn(new TestTypeUsage(false, Set.of(), null));

    final ImpactAnalysisResult result =
        newEngine(directConfig()).analyzeImpact(tempDir, Set.of(mainDir), Set.of(testDir));

    assertThat(result.relevantTestsWithCauses()).isEmpty();
    verify(testImpactEvaluator, never()).evaluateCauses(any(), any(), any(), any(), any(), any());
  }

  /** A test with the annotation but no matching causes must be filtered out of the result. */
  @Test
  void directModeExcludesTestWithEmptyCauses() throws IOException {
    final Path mainDir = createDir(tempDir.resolve("src/main/java"));
    final Path testDir = createDir(tempDir.resolve("src/test/java"));
    writeJavaFile(testDir, "UnrelatedTest.java", "public class UnrelatedTest {}");
    final Path changedFile = mainDir.resolve("A.java");

    when(changedClassLocator.findChangedClassPaths(Set.of(mainDir), tempDir))
        .thenReturn(Set.of(changedFile));
    when(changedTypeSeedResolver.resolve(Set.of(changedFile), tempDir))
        .thenReturn(new ChangedTypeSeedData(Map.of(), Map.of("A", Set.of("A")), Set.of("A")));
    when(testTypeUsageExtractor.extract(any(CompilationUnit.class)))
        .thenReturn(new TestTypeUsage(true, Set.of("Unrelated"), "com.example.UnrelatedTest"));
    when(testMockUsageExtractor.extractMockedTypes(any(CompilationUnit.class)))
        .thenReturn(Set.of());
    when(testImpactEvaluator.evaluateCauses(any(), any(), any(), any(), any(), any()))
        .thenReturn(Set.of());

    final ImpactAnalysisResult result =
        newEngine(directConfig()).analyzeImpact(tempDir, Set.of(mainDir), Set.of(testDir));

    assertThat(result.relevantTestsWithCauses()).isEmpty();
  }

  /** Transitive mode must delegate to the transitive propagator with the configured depth. */
  @Test
  void transitiveModeUsesTransitivePropagator() throws IOException {
    final Path mainDir = createDir(tempDir.resolve("src/main/java"));
    final Path testDir = createDir(tempDir.resolve("src/test/java"));
    final Path changedFile = mainDir.resolve("A.java");

    when(changedClassLocator.findChangedClassPaths(Set.of(mainDir), tempDir))
        .thenReturn(Set.of(changedFile));
    when(changedTypeSeedResolver.resolve(Set.of(changedFile), tempDir))
        .thenReturn(new ChangedTypeSeedData(Map.of(), Map.of("A", Set.of("A")), Set.of("A")));
    when(mainSourceIndexBuilder.build(Set.of(mainDir)))
        .thenReturn(new TypeDependencyIndex(Map.of(), Map.of(), Map.of()));
    when(transitiveImpactPropagator.propagate(any(), any(), anyInt()))
        .thenReturn(new ImpactPropagationResult(Map.of(), Map.of()));

    newEngine(transitiveConfig(MockPolicy.FILTER_MOCKED_PATHS))
        .analyzeImpact(tempDir, Set.of(mainDir), Set.of(testDir));

    verify(transitiveImpactPropagator).propagate(eq(Map.of("A", Set.of("A"))), eq(Map.of()), eq(2));
  }

  /**
   * Transitive mode combined with CURRENT policy must be promoted to FILTER_MOCKED_PATHS — CURRENT
   * has no useful semantics on propagated paths.
   */
  @Test
  void transitiveModeForcesCurrentPolicyToFilterMockedPaths() throws IOException {
    final Path mainDir = createDir(tempDir.resolve("src/main/java"));
    final Path testDir = createDir(tempDir.resolve("src/test/java"));
    writeJavaFile(testDir, "ATest.java", "public class ATest {}");
    final Path changedFile = mainDir.resolve("A.java");

    when(changedClassLocator.findChangedClassPaths(Set.of(mainDir), tempDir))
        .thenReturn(Set.of(changedFile));
    when(changedTypeSeedResolver.resolve(Set.of(changedFile), tempDir))
        .thenReturn(new ChangedTypeSeedData(Map.of(), Map.of("A", Set.of("A")), Set.of("A")));
    when(mainSourceIndexBuilder.build(Set.of(mainDir)))
        .thenReturn(new TypeDependencyIndex(Map.of(), Map.of(), Map.of()));
    when(transitiveImpactPropagator.propagate(any(), any(), anyInt()))
        .thenReturn(new ImpactPropagationResult(Map.of(), Map.of()));
    when(testTypeUsageExtractor.extract(any(CompilationUnit.class)))
        .thenReturn(new TestTypeUsage(true, Set.of("A"), "com.example.ATest"));
    when(testMockUsageExtractor.extractMockedTypes(any(CompilationUnit.class)))
        .thenReturn(Set.of());
    when(testImpactEvaluator.evaluateCauses(any(), any(), any(), any(), any(), any()))
        .thenReturn(Set.of());

    newEngine(transitiveConfig(MockPolicy.CURRENT))
        .analyzeImpact(tempDir, Set.of(mainDir), Set.of(testDir));

    verify(testImpactEvaluator)
        .evaluateCauses(any(), any(), any(), any(), any(), eq(MockPolicy.FILTER_MOCKED_PATHS));
  }

  /** Only tests with non-empty causes should appear in the result; siblings must be filtered. */
  @Test
  void multipleTestFilesReturnOnlyImpactedOnes() throws IOException {
    final Path mainDir = createDir(tempDir.resolve("src/main/java"));
    final Path testDir = createDir(tempDir.resolve("src/test/java"));
    final Path impactedTest = writeJavaFile(testDir, "ATest.java", "public class ATest {}");
    final Path unrelatedTest = writeJavaFile(testDir, "BTest.java", "public class BTest {}");
    final Path changedFile = mainDir.resolve("A.java");

    when(changedClassLocator.findChangedClassPaths(Set.of(mainDir), tempDir))
        .thenReturn(Set.of(changedFile));
    when(changedTypeSeedResolver.resolve(Set.of(changedFile), tempDir))
        .thenReturn(new ChangedTypeSeedData(Map.of(), Map.of("A", Set.of("A")), Set.of("A")));
    when(testTypeUsageExtractor.extract(any(CompilationUnit.class)))
        .thenAnswer(
            invocation -> {
              final CompilationUnit cu = invocation.getArgument(0);
              final String source = cu.toString();
              if (source.contains("ATest")) {
                return new TestTypeUsage(true, Set.of("A"), "com.example.ATest");
              }
              return new TestTypeUsage(true, Set.of("Other"), "com.example.BTest");
            });
    when(testMockUsageExtractor.extractMockedTypes(any(CompilationUnit.class)))
        .thenReturn(Set.of());
    when(testImpactEvaluator.evaluateCauses(eq(Set.of("A")), any(), any(), any(), any(), any()))
        .thenReturn(Set.of("A"));
    when(testImpactEvaluator.evaluateCauses(eq(Set.of("Other")), any(), any(), any(), any(), any()))
        .thenReturn(Set.of());

    final ImpactAnalysisResult result =
        newEngine(directConfig()).analyzeImpact(tempDir, Set.of(mainDir), Set.of(testDir));

    assertThat(result.relevantTestsWithCauses()).containsOnlyKeys(impactedTest);
    assertThat(result.relevantTestsWithCauses().get(impactedTest)).containsExactly("A");
    assertThat(result.relevantTestsWithCauses()).doesNotContainKey(unrelatedTest);
  }

  private ImpactAnalysisEngine newEngine(final ImpactCheckerConfig config) {
    return new ImpactAnalysisEngine(
        new JavaParser(),
        config,
        changedClassLocator,
        changedTypeSeedResolver,
        mainSourceIndexBuilder,
        transitiveImpactPropagator,
        testTypeUsageExtractor,
        testMockUsageExtractor,
        testImpactEvaluator);
  }

  private static ImpactCheckerConfig directConfig() {
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setAnalysisMode(AnalysisMode.DIRECT);
    config.setMockPolicy(MockPolicy.CURRENT);
    return config;
  }

  private static ImpactCheckerConfig transitiveConfig(final MockPolicy mockPolicy) {
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setAnalysisMode(AnalysisMode.TRANSITIVE);
    config.setMockPolicy(mockPolicy);
    config.setMaxPropagationDepth(2);
    return config;
  }

  private static Path createDir(final Path dir) throws IOException {
    Files.createDirectories(dir);
    return dir;
  }

  private static Path writeJavaFile(final Path dir, final String fileName, final String content)
      throws IOException {
    final Path file = dir.resolve(fileName);
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }
}
