package io.github.hillemacher.testimpactchecker;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.java.JavaImpactUtils;
import io.github.hillemacher.testimpactchecker.java.analysis.ImpactAnalysisEngine;
import io.github.hillemacher.testimpactchecker.java.analysis.ImpactAnalysisResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/** Orchestrates test impact analysis for a repository by combining Git and Java parsing helpers. */
@Slf4j
public class TestImpactChecker {

  private static final String MAIN_JAVA_DIR_SUFFIX = "src/main/java";
  private static final String TEST_JAVA_DIR_SUFFIX = "src/test/java";

  /**
   * Detects and returns the set of test files that are impacted by changes in the main Java source
   * files of the specified repository.
   *
   * <p>This method performs the following steps:
   *
   * <ol>
   *   <li>Recursively locates all {@code src/main/java} and {@code src/test/java} directories
   *       within the repository.
   *   <li>Identifies changed Java class files under all {@code src/main/java} directories using
   *       Git.
   *   <li>If no changed classes are found or the repository cannot be accessed, returns an empty
   *       set.
   *   <li>For each changed class, determines its implemented interfaces.
   *   <li>Scans all test classes for relevant annotations and references to the changed classes or
   *       their implemented interfaces, and collects the test files that are likely to be impacted.
   * </ol>
   *
   * <p>The result is a set of paths to test files that are potentially affected by the changes.
   *
   * @param repositoryPath the root path of the repository to scan
   * @param impactCheckerConfig configuration defining annotations, Git refs, and analysis controls
   * @return a set of paths to impacted test files; returns an empty set if no changed classes are
   *     detected
   * @throws IOException if file system operations fail while scanning source directories
   */
  public Set<Path> detectImpact(
      final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) throws IOException {
    return new HashSet<>(
        detectImpactReportData(repositoryPath, impactCheckerConfig)
            .relevantTestsWithCauses()
            .keySet());
  }

  /**
   * Detects impacted tests and returns each impacted test with the changed classes that caused it.
   *
   * @param repositoryPath the root path of the repository to scan
   * @param impactCheckerConfig configuration for annotations and git refs
   * @return a map where the key is an impacted test path and the value is the set of changed class
   *     names causing the impact
   * @throws IOException if file system operations fail while scanning source directories
   */
  public Map<Path, Set<String>> detectImpactWithCauses(
      final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) throws IOException {
    return detectImpactReportData(repositoryPath, impactCheckerConfig).relevantTestsWithCauses();
  }

  /**
   * Detects impact and returns both test-level and propagated type-level cause mappings.
   *
   * @param repositoryPath the root path of the repository to scan
   * @param impactCheckerConfig configuration for annotations and git refs
   * @return report-ready impact model containing impacted tests and impacted types with causes
   * @throws IOException if file system operations fail while scanning source directories
   */
  public ImpactDetectionReportData detectImpactReportData(
      final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) throws IOException {
    log.info("Discovering Java source directories under {}", repositoryPath);
    final JavaImpactUtils javaImpactUtils = createJavaImpactUtils(impactCheckerConfig);

    // 1. Find all src/main/java directories in project (recursively)
    final Set<Path> mainJavaDirs = findAllJavaSourceDirs(repositoryPath, MAIN_JAVA_DIR_SUFFIX);
    final Set<Path> testJavaDirs = findAllJavaSourceDirs(repositoryPath, TEST_JAVA_DIR_SUFFIX);
    log.info(
        "Discovered {} main Java source directories and {} test Java source directories",
        mainJavaDirs.size(),
        testJavaDirs.size());
    log.debug("Main Java source directories: {}", mainJavaDirs);
    log.debug("Test Java source directories: {}", testJavaDirs);

    final ImpactAnalysisEngine impactAnalysisEngine = javaImpactUtils.createEngine();
    final ImpactAnalysisResult impactAnalysisResult =
        impactAnalysisEngine.analyzeImpact(repositoryPath, mainJavaDirs, testJavaDirs);
    final Map<Path, Set<String>> relevantTestsWithCauses =
        impactAnalysisResult.relevantTestsWithCauses();
    log.info("Detected {} impacted tests", relevantTestsWithCauses.size());
    log.debug("Impacted tests with causes: {}", relevantTestsWithCauses);
    return new ImpactDetectionReportData(
        relevantTestsWithCauses, impactAnalysisResult.propagationResult().impactedTypeToCauses());
  }

  // Recursively find all src/main/java or src/test/java dirs from a given root
  private Set<Path> findAllJavaSourceDirs(final Path root, final String part) {
    final Set<Path> dirs = new HashSet<>();
    try (final Stream<Path> paths = Files.walk(root)) {
      paths
          .filter(Files::isDirectory)
          .filter(p -> p.toString().replace(File.separator, "/").endsWith(part))
          .forEach(dirs::add);
    } catch (final IOException e) {
      log.error("Cannot find Java source files", e);
    }

    return dirs;
  }

  private JavaImpactUtils createJavaImpactUtils(final ImpactCheckerConfig impactCheckerConfig) {
    final ParserConfiguration parserConfiguration = new ParserConfiguration();
    parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    final JavaParser parser = new JavaParser(parserConfiguration);
    return new JavaImpactUtils(parser, impactCheckerConfig);
  }
}
