package io.github.hillemacher.testimpactchecker.java;

import com.github.javaparser.JavaParser;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.java.analysis.ChangedClassLocator;
import io.github.hillemacher.testimpactchecker.java.analysis.ChangedTypeSeedResolver;
import io.github.hillemacher.testimpactchecker.java.analysis.ImpactAnalysisEngine;
import io.github.hillemacher.testimpactchecker.java.analysis.MainSourceIndexBuilder;
import io.github.hillemacher.testimpactchecker.java.analysis.TestImpactEvaluator;
import io.github.hillemacher.testimpactchecker.java.analysis.TestMockUsageExtractor;
import io.github.hillemacher.testimpactchecker.java.analysis.TestTypeUsageExtractor;
import io.github.hillemacher.testimpactchecker.java.analysis.TransitiveImpactPropagator;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

/**
 * Java source analysis utilities for mapping changed classes to impacted tests.
 */
@AllArgsConstructor
public class JavaImpactUtils {

  private final JavaParser javaParser;

  private final ImpactCheckerConfig impactCheckerConfig;

  /**
   * Computes impacted tests using the direct reference model.
   *
   * <p>This method is intentionally conservative and does not perform transitive dependency
   * traversal. A test is considered impacted when it references a changed class directly or
   * references an interface implemented by a changed class, and then survives mock filtering
   * according to the configured policy.
   *
   * @param changedFilePaths changed class file paths (relative to repository root)
   * @param implementedInterfaces map of changed class paths to directly implemented interface names
   * @param testDirPaths directories containing Java test sources
   * @return impacted test file paths as absolute paths
   */
  public Set<Path> findRelevantTests(
      final Collection<Path> changedFilePaths,
      final Map<Path, Set<String>> implementedInterfaces,
      final Collection<Path> testDirPaths) {
    return new HashSet<>(findRelevantTestsWithCauses(changedFilePaths, implementedInterfaces, testDirPaths).keySet());
  }

  /**
   * Computes impacted tests with explicit root-cause attribution in direct reference mode.
   *
   * <p>The returned cause set contains changed class names, even when a match occurred through
   * an implemented interface. This keeps downstream reporting stable and allows callers to map
   * all impacts back to the actual changed production classes.
   *
   * @param changedFilePaths changed class file paths (relative to repository root)
   * @param implementedInterfaces map of changed class paths to directly implemented interface names
   * @param testDirPaths directories containing Java test sources
   * @return map from impacted test file path to root changed-class names that caused inclusion
   */
  public Map<Path, Set<String>> findRelevantTestsWithCauses(
      final Collection<Path> changedFilePaths,
      final Map<Path, Set<String>> implementedInterfaces,
      final Collection<Path> testDirPaths) {
    final Set<String> changedClassNames = changedFilePaths.stream()
        .map(this::toSimpleClassName)
        .collect(Collectors.toSet());

    return createEngine().analyzeTestsWithDirectModel(
        changedFilePaths,
        implementedInterfaces,
        new HashSet<>(testDirPaths),
        changedClassNames,
        impactCheckerConfig.getMockPolicy());
  }

  /**
   * Locates changed Java classes under the configured main-source directories.
   *
   * <p>This delegates Git diff resolution to {@link ChangedClassLocator}, then filters results
   * to Java files that are inside discovered {@code src/main/java} roots. Returned paths are
   * normalized and relative to the repository root to simplify later matching and reporting.
   *
   * @param mainJavaDirs directories considered part of main Java sources
   * @param gitRepoPath root path of the git repository
   * @return set of changed Java class paths relative to repository root
   */
  public Set<Path> findChangedClassPaths(
      final Collection<Path> mainJavaDirs, final Path gitRepoPath) {
    return new ChangedClassLocator(impactCheckerConfig)
        .findChangedClassPaths(new HashSet<>(mainJavaDirs), gitRepoPath);
  }

  /**
   * Resolves directly implemented interfaces for each changed class.
   *
   * <p>The interface names are captured as simple names because the current analyzer operates
   * on simple-name matching. This data is used both in direct matching and as seed types for
   * transitive propagation.
   *
   * @param changedClassPaths changed class file paths (relative to repository root)
   * @param repoRoot repository root used to resolve file paths
   * @return map of changed class path to implemented interface simple names
   */
  public Map<Path, Set<String>> findImplementedInterfaces(
      final Collection<Path> changedClassPaths, final Path repoRoot) {
    return new ChangedTypeSeedResolver(javaParser).findImplementedInterfaces(changedClassPaths, repoRoot);
  }

  /**
   * Creates a fully wired analysis engine with the modular pipeline components.
   *
   * <p>Keeping engine construction in one place guarantees consistent behavior between
   * direct and transitive callers and avoids spreading object wiring across orchestrators.
   *
   * @return configured {@link ImpactAnalysisEngine} instance
   */
  public ImpactAnalysisEngine createEngine() {
    return new ImpactAnalysisEngine(
        javaParser,
        impactCheckerConfig,
        new ChangedClassLocator(impactCheckerConfig),
        new ChangedTypeSeedResolver(javaParser),
        new MainSourceIndexBuilder(javaParser),
        new TransitiveImpactPropagator(),
        new TestTypeUsageExtractor(impactCheckerConfig),
        new TestMockUsageExtractor(),
        new TestImpactEvaluator());
  }

  private String toSimpleClassName(final Path path) {
    final String fileName = path.getFileName().toString();
    final int extensionIndex = fileName.lastIndexOf('.');
    if (extensionIndex < 0) {
      return fileName;
    }
    return fileName.substring(0, extensionIndex);
  }
}
