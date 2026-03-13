package io.github.hillemacher.testimpactchecker.java.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class ImpactAnalysisEngine {

  private final JavaParser javaParser;
  private final ImpactCheckerConfig impactCheckerConfig;
  private final ChangedClassLocator changedClassLocator;
  private final ChangedTypeSeedResolver changedTypeSeedResolver;
  private final MainSourceIndexBuilder mainSourceIndexBuilder;
  private final TransitiveImpactPropagator transitiveImpactPropagator;
  private final TestTypeUsageExtractor testTypeUsageExtractor;
  private final TestMockUsageExtractor testMockUsageExtractor;
  private final TestImpactEvaluator testImpactEvaluator;

  /**
   * Runs end-to-end impact analysis and returns impacted tests with root changed-class causes.
   *
   * <p>This is the primary orchestration entry point for repository analysis. It performs:
   * <ol>
   * <li>changed-class discovery from Git</li>
   * <li>seed extraction from changed classes and implemented interfaces</li>
   * <li>direct or transitive propagation, depending on configuration</li>
   * <li>test-file parsing and final cause evaluation with mock filtering</li>
   * </ol>
   * The method intentionally returns an empty map when no changed classes are found.
   *
   * @param repositoryPath repository root path
   * @param mainJavaDirs directories containing main Java sources
   * @param testJavaDirs directories containing test Java sources
   * @return map from impacted test file to root changed-class causes
   */
  public Map<Path, Set<String>> analyzeImpactWithCauses(
      final Path repositoryPath,
      final Set<Path> mainJavaDirs,
      final Set<Path> testJavaDirs) {
    return analyzeImpact(repositoryPath, mainJavaDirs, testJavaDirs).relevantTestsWithCauses();
  }

  /**
   * Runs end-to-end impact analysis and returns both impacted tests and propagated type topology.
   *
   * @param repositoryPath repository root path
   * @param mainJavaDirs directories containing main Java sources
   * @param testJavaDirs directories containing test Java sources
   * @return full analysis result for report generation and diagnostics
   */
  public ImpactAnalysisResult analyzeImpact(
      final Path repositoryPath,
      final Set<Path> mainJavaDirs,
      final Set<Path> testJavaDirs) {
    final Set<Path> changedClassPaths = changedClassLocator.findChangedClassPaths(mainJavaDirs, repositoryPath);
    if (changedClassPaths.isEmpty()) {
      return new ImpactAnalysisResult(Map.of(), new ImpactPropagationResult(Map.of(), Map.of()));
    }

    final ChangedTypeSeedData changedTypeSeedData = changedTypeSeedResolver.resolve(changedClassPaths,
        repositoryPath);
    final ImpactPropagationResult propagationResult = buildPropagationResult(changedTypeSeedData,
        mainJavaDirs);

    final Map<Path, Set<String>> relevantTestsWithCauses = analyzeTests(
        propagationResult,
        changedTypeSeedData.changedClassNames(),
        testJavaDirs,
        resolveMockPolicy());
    return new ImpactAnalysisResult(relevantTestsWithCauses, propagationResult);
  }

  /**
   * Evaluates tests using the direct seed model without transitive propagation.
   *
   * <p>This method is used to preserve existing behavior and enables direct-mode execution
   * from higher-level callers without building the main-source dependency graph.
   *
   * @param changedFilePaths changed class file paths relative to repository root
   * @param implementedInterfaces map of changed class path to implemented interfaces
   * @param testDirPaths directories containing test Java sources
   * @param changedClassNames simple changed class names
   * @param mockPolicy mock filtering policy to apply during evaluation
   * @return map from impacted test file to changed-class causes
   */
  public Map<Path, Set<String>> analyzeTestsWithDirectModel(
      final Collection<Path> changedFilePaths,
      final Map<Path, Set<String>> implementedInterfaces,
      final Set<Path> testDirPaths,
      final Set<String> changedClassNames,
      final MockPolicy mockPolicy) {
    final Map<String, Set<String>> impactedTypeToCauses = new HashMap<>();
    final Map<String, Map<String, Set<List<String>>>> witnessPathsByTypeAndCause = new HashMap<>();

    changedFilePaths.forEach(changedFilePath -> {
      final String changedClass = toSimpleClassName(changedFilePath);
      impactedTypeToCauses.computeIfAbsent(changedClass, key -> new HashSet<>()).add(changedClass);
      witnessPathsByTypeAndCause.computeIfAbsent(changedClass, key -> new HashMap<>())
          .computeIfAbsent(changedClass, key -> new HashSet<>())
          .add(List.of(changedClass));

      implementedInterfaces.getOrDefault(changedFilePath, Set.of()).forEach(interfaceName -> {
        impactedTypeToCauses.computeIfAbsent(interfaceName, key -> new HashSet<>()).add(changedClass);
        witnessPathsByTypeAndCause.computeIfAbsent(interfaceName, key -> new HashMap<>())
            .computeIfAbsent(changedClass, key -> new HashSet<>())
            .add(List.of(interfaceName));
      });
    });

    return analyzeTests(
        new ImpactPropagationResult(impactedTypeToCauses, witnessPathsByTypeAndCause),
        changedClassNames,
        testDirPaths,
        mockPolicy);
  }

  private ImpactPropagationResult buildPropagationResult(
      final ChangedTypeSeedData changedTypeSeedData,
      final Set<Path> mainJavaDirs) {
    if (impactCheckerConfig.getAnalysisMode() == AnalysisMode.DIRECT) {
      return buildDirectPropagationResult(changedTypeSeedData.seedTypeToChangedClasses());
    }

    final TypeDependencyIndex dependencyIndex = mainSourceIndexBuilder.build(mainJavaDirs);
    return transitiveImpactPropagator.propagate(
        changedTypeSeedData.seedTypeToChangedClasses(),
        dependencyIndex.reverseDependencies(),
        impactCheckerConfig.getMaxPropagationDepth());
  }

  private ImpactPropagationResult buildDirectPropagationResult(
      final Map<String, Set<String>> seedTypeToChangedClasses) {
    final Map<String, Set<String>> impactedTypeToCauses = new HashMap<>();
    final Map<String, Map<String, Set<List<String>>>> witnessPathsByTypeAndCause = new HashMap<>();

    seedTypeToChangedClasses.forEach((seedType, causes) -> {
      impactedTypeToCauses.computeIfAbsent(seedType, key -> new HashSet<>()).addAll(causes);
      final Map<String, Set<List<String>>> causeToPaths =
          witnessPathsByTypeAndCause.computeIfAbsent(seedType, key -> new HashMap<>());
      causes.forEach(cause -> causeToPaths.computeIfAbsent(cause, key -> new HashSet<>()).add(List.of(seedType)));
    });

    return new ImpactPropagationResult(impactedTypeToCauses, witnessPathsByTypeAndCause);
  }

  private Map<Path, Set<String>> analyzeTests(
      final ImpactPropagationResult propagationResult,
      final Set<String> changedClassNames,
      final Set<Path> testDirPaths,
      final MockPolicy mockPolicy) {
    final Map<Path, Set<String>> relevantTestsWithCauses = new HashMap<>();
    final Set<Path> allTestFiles = new HashSet<>();
    testDirPaths.forEach(testDirPath -> allTestFiles.addAll(getAllJavaFiles(testDirPath)));

    for (final Path testFilePath : allTestFiles) {
      final ParseResult<CompilationUnit> parseResult = parseJavaSourceFile(testFilePath);
      if (parseResult == null || !parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
        continue;
      }

      final CompilationUnit compilationUnit = parseResult.getResult().get();
      final TestTypeUsage testTypeUsage = testTypeUsageExtractor.extract(compilationUnit);
      if (!testTypeUsage.hasRequiredAnnotation()) {
        continue;
      }

      final Set<String> mockedTypes = testMockUsageExtractor.extractMockedTypes(compilationUnit);
      final Set<String> causesForTest = testImpactEvaluator.evaluateCauses(
          testTypeUsage.referencedTypes(),
          mockedTypes,
          changedClassNames,
          propagationResult.impactedTypeToCauses(),
          propagationResult.witnessPathsByTypeAndCause(),
          mockPolicy);

      if (!causesForTest.isEmpty()) {
        relevantTestsWithCauses.put(testFilePath, causesForTest);
      }
    }

    return relevantTestsWithCauses;
  }

  private ParseResult<CompilationUnit> parseJavaSourceFile(final Path javaSourceFile) {
    try (final FileInputStream in = new FileInputStream(javaSourceFile.toFile())) {
      return javaParser.parse(in);
    } catch (final IOException ex) {
      log.error("Failed to parse {}: {}", javaSourceFile, ex.getMessage());
      return null;
    }
  }

  private Set<Path> getAllJavaFiles(final Path dir) {
    if (!Files.exists(dir)) {
      return Set.of();
    }

    try (final Stream<Path> paths = Files.walk(dir)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .collect(Collectors.toSet());
    } catch (final IOException e) {
      log.debug("Cannot process {}", dir, e);
      return Set.of();
    }
  }

  private MockPolicy resolveMockPolicy() {
    if (impactCheckerConfig.getAnalysisMode() == AnalysisMode.TRANSITIVE
        && impactCheckerConfig.getMockPolicy() == MockPolicy.CURRENT) {
      return MockPolicy.FILTER_MOCKED_PATHS;
    }
    return impactCheckerConfig.getMockPolicy();
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
