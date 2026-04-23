package io.github.hillemacher.testimpactchecker;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.java.JavaImpactUtils;
import io.github.hillemacher.testimpactchecker.java.analysis.ImpactAnalysisEngine;
import io.github.hillemacher.testimpactchecker.java.analysis.ImpactAnalysisResult;
import io.github.hillemacher.testimpactchecker.java.analysis.cache.JsonFileTypeIndexCache;
import io.github.hillemacher.testimpactchecker.java.analysis.cache.NoOpTypeIndexCache;
import io.github.hillemacher.testimpactchecker.java.analysis.cache.TypeIndexCache;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/** Orchestrates test impact analysis for a repository by combining Git and Java parsing helpers. */
@Slf4j
public class TestImpactChecker {

  /** Default cache directory name relative to the project path when none is configured. */
  public static final String DEFAULT_CACHE_DIRECTORY_NAME = ".testimpactchecker/cache";

  /** Default cache file name inside the cache directory. */
  public static final String TYPE_INDEX_CACHE_FILE = "type-index.v1.json";

  private static final String MAIN_JAVA_DIR_SUFFIX = "src/main/java";
  private static final String TEST_JAVA_DIR_SUFFIX = "src/test/java";

  /**
   * Cache policy controlling whether analysis reads from, writes to, or verifies the persistent
   * type-index cache.
   */
  public enum CacheMode {
    /** Use the persistent cache at {@code <project>/.testimpactchecker/cache/}. */
    ENABLED,
    /** Bypass the cache entirely — no reads, no writes. */
    DISABLED,
    /**
     * Run the analysis twice (uncached then cached) and assert both produce equal results. Useful
     * as a one-off correctness check before trusting the cache in CI.
     */
    VERIFY
  }

  public Set<Path> detectImpact(
      final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) throws IOException {
    return new HashSet<>(
        detectImpactReportData(repositoryPath, impactCheckerConfig)
            .relevantTestsWithCauses()
            .keySet());
  }

  public Map<Path, Set<String>> detectImpactWithCauses(
      final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) throws IOException {
    return detectImpactReportData(repositoryPath, impactCheckerConfig).relevantTestsWithCauses();
  }

  /** Runs impact detection with the default cache mode ({@link CacheMode#ENABLED}). */
  public ImpactDetectionReportData detectImpactReportData(
      final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) throws IOException {
    return detectImpactReportData(repositoryPath, impactCheckerConfig, CacheMode.ENABLED);
  }

  /**
   * Detects impact and returns both test-level and propagated type-level cause mappings.
   *
   * @param repositoryPath the root path of the repository to scan
   * @param impactCheckerConfig configuration for annotations and git refs
   * @param cacheMode how the persistent main-source index cache should be used
   * @return report-ready impact model containing impacted tests and impacted types with causes
   * @throws IOException if file system operations fail while scanning source directories
   */
  public ImpactDetectionReportData detectImpactReportData(
      final Path repositoryPath,
      final ImpactCheckerConfig impactCheckerConfig,
      final CacheMode cacheMode)
      throws IOException {
    log.info("Discovering Java source directories under {}", repositoryPath);
    final Set<Path> mainJavaDirs = findAllJavaSourceDirs(repositoryPath, MAIN_JAVA_DIR_SUFFIX);
    final Set<Path> testJavaDirs = findAllJavaSourceDirs(repositoryPath, TEST_JAVA_DIR_SUFFIX);
    log.info(
        "Discovered {} main Java source directories and {} test Java source directories",
        mainJavaDirs.size(),
        testJavaDirs.size());
    log.debug("Main Java source directories: {}", mainJavaDirs);
    log.debug("Test Java source directories: {}", testJavaDirs);

    final JavaImpactUtils javaImpactUtils = createJavaImpactUtils(impactCheckerConfig);

    if (cacheMode == CacheMode.VERIFY) {
      log.info("Running analysis twice to verify cache parity");
      final ImpactAnalysisResult uncached =
          runAnalysis(
              javaImpactUtils,
              new NoOpTypeIndexCache(),
              repositoryPath,
              mainJavaDirs,
              testJavaDirs);
      final ImpactAnalysisResult cached =
          runAnalysis(
              javaImpactUtils,
              openCache(repositoryPath, impactCheckerConfig),
              repositoryPath,
              mainJavaDirs,
              testJavaDirs);
      assertCacheParity(uncached, cached);
      return toReportData(cached);
    }

    final TypeIndexCache typeIndexCache =
        cacheMode == CacheMode.ENABLED
            ? openCache(repositoryPath, impactCheckerConfig)
            : new NoOpTypeIndexCache();
    final ImpactAnalysisResult result =
        runAnalysis(javaImpactUtils, typeIndexCache, repositoryPath, mainJavaDirs, testJavaDirs);
    return toReportData(result);
  }

  /**
   * Resolves the effective cache directory for a project: the configured {@code cacheDirectoryPath}
   * when set, otherwise {@code <project>/.testimpactchecker/cache/}. Relative configured paths are
   * resolved against {@code repositoryPath}.
   */
  public static Path resolveCacheDirectory(
      final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) {
    final String configured =
        impactCheckerConfig == null ? null : impactCheckerConfig.getCacheDirectoryPath();
    if (configured == null || configured.isBlank()) {
      return repositoryPath.resolve(DEFAULT_CACHE_DIRECTORY_NAME);
    }
    final Path configuredPath = Path.of(configured);
    if (configuredPath.isAbsolute()) {
      return configuredPath;
    }
    return repositoryPath.resolve(configuredPath);
  }

  /**
   * Deletes the cache directory for the given project and config; safe to call when it does not
   * exist. Honors a configured {@code cacheDirectoryPath} when present.
   */
  public static void clearCache(
      final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) throws IOException {
    final Path cacheDir = resolveCacheDirectory(repositoryPath, impactCheckerConfig);
    if (!Files.exists(cacheDir)) {
      return;
    }
    try (final Stream<Path> paths = Files.walk(cacheDir)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (final IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }

  private ImpactAnalysisResult runAnalysis(
      final JavaImpactUtils javaImpactUtils,
      final TypeIndexCache typeIndexCache,
      final Path repositoryPath,
      final Set<Path> mainJavaDirs,
      final Set<Path> testJavaDirs) {
    final ImpactAnalysisEngine engine = javaImpactUtils.createEngine(typeIndexCache);
    final ImpactAnalysisResult result =
        engine.analyzeImpact(repositoryPath, mainJavaDirs, testJavaDirs);
    log.info("Detected {} impacted tests", result.relevantTestsWithCauses().size());
    log.debug("Impacted tests with causes: {}", result.relevantTestsWithCauses());
    return result;
  }

  private static ImpactDetectionReportData toReportData(final ImpactAnalysisResult result) {
    return new ImpactDetectionReportData(
        result.relevantTestsWithCauses(),
        result.propagationResult().impactedTypeToCauses(),
        result.testFileToFqcn());
  }

  private static TypeIndexCache openCache(
      final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) {
    final Path cacheFile =
        resolveCacheDirectory(repositoryPath, impactCheckerConfig).resolve(TYPE_INDEX_CACHE_FILE);
    return new JsonFileTypeIndexCache(cacheFile);
  }

  private static void assertCacheParity(
      final ImpactAnalysisResult uncached, final ImpactAnalysisResult cached) {
    if (!uncached.relevantTestsWithCauses().equals(cached.relevantTestsWithCauses())) {
      throw new IllegalStateException(
          "Cache verification failed: impacted-tests set differs between cached and uncached runs");
    }
    if (!uncached
        .propagationResult()
        .impactedTypeToCauses()
        .equals(cached.propagationResult().impactedTypeToCauses())) {
      throw new IllegalStateException(
          "Cache verification failed: impacted-types set differs between cached and uncached runs");
    }
    log.info("Cache verification passed: cached and uncached results are identical");
  }

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
