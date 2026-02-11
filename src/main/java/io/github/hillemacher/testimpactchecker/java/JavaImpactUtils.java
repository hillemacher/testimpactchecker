package io.github.hillemacher.testimpactchecker.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.git.GitImpactUtils;
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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.diff.DiffEntry;

/**
 * Java source analysis utilities for mapping changed classes to impacted tests.
 */
@AllArgsConstructor
@Slf4j
public class JavaImpactUtils {

  private final JavaParser javaParser;

  private final ImpactCheckerConfig impactCheckerConfig;

  /**
   * Identifies relevant test files that are likely to be affected by changes in
   * the specified set
   * of Java files.
   *
   * <p>
   * This method scans all Java test files found under the provided
   * {@code testDirPaths}, parses
   * each test file, and determines whether it is relevant to the changed files
   * based on the
   * following criteria:
   *
   * <ul>
   * <li>The test class must have at least one annotation matching a predefined
   * set.
   * <li>The test must directly reference (by simple class name) any changed class
   * or interface it
   * implements, excluding cases where the class is only used as a mock (i.e., in
   * {@code
   *       mock(SomeClass.class)} calls).
   * </ul>
   *
   * <p>
   * If a test file is determined to be relevant, its path is included in the
   * result.
   *
   * <p>
   * Files that cannot be parsed or do not meet the above criteria are ignored.
   *
   * @param changedFilePaths      a collection of paths to changed Java files
   * @param implementedInterfaces a map from changed class paths to sets of
   *                              implemented interface
   *                              names
   * @param testDirPaths          a collection of paths to directories containing
   *                              test Java files
   * @return a set of paths to relevant test files that reference changed classes
   *         or their
   *         implemented interfaces, excluding those that only mock the changed
   *         classes
   */
  public Set<Path> findRelevantTests(
      final Collection<Path> changedFilePaths,
      final Map<Path, Set<String>> implementedInterfaces,
      final Collection<Path> testDirPaths) {
    return new HashSet<>(findRelevantTestsWithCauses(changedFilePaths, implementedInterfaces, testDirPaths).keySet());
  }

  /**
   * Identifies relevant test files and the changed classes that caused each test to be included.
   *
   * @param changedFilePaths      a collection of paths to changed Java files
   * @param implementedInterfaces a map from changed class paths to sets of implemented interface
   *                              names
   * @param testDirPaths          a collection of paths to directories containing test Java files
   * @return a map where the key is an impacted test file path and the value is the set of changed
   *         class names causing the impact
   */
  public Map<Path, Set<String>> findRelevantTestsWithCauses(
      final Collection<Path> changedFilePaths,
      final Map<Path, Set<String>> implementedInterfaces,
      final Collection<Path> testDirPaths) {
    final Map<Path, Set<String>> relevantTestsWithCauses = new HashMap<>();

    final Set<Path> allTestFiles = new HashSet<>();
    testDirPaths.forEach(testDirPath -> allTestFiles.addAll(getAllJavaFiles(testDirPath)));
    final Map<String, Set<String>> referencedTypeToChangedClasses =
        buildReferencedTypeToChangedClasses(changedFilePaths, implementedInterfaces);
    final Set<String> changedClassNames = changedFilePaths.stream()
        .map(this::toSimpleClassName)
        .collect(Collectors.toSet());

    for (final Path testFilePath : allTestFiles) {
      final CompilationUnit compilationUnit;
      try (final FileInputStream in = new FileInputStream(testFilePath.toFile())) {
        final ParseResult<CompilationUnit> compilationUnitParseResult = javaParser.parse(in);
        if (!compilationUnitParseResult.isSuccessful()
            || compilationUnitParseResult.getResult().isEmpty()) {
          log.error("Failed to parse {}", testFilePath);
          log.error("Failed result {}", compilationUnitParseResult);
          continue;
        }
        compilationUnit = compilationUnitParseResult.getResult().get();
      } catch (final IOException ex) {
        log.error("Failed to parse {}: {}", testFilePath, ex.getMessage());
        continue;
      }

      final ClassOrInterfaceDeclaration classDecl = compilationUnit.findFirst(ClassOrInterfaceDeclaration.class)
          .orElse(null);
      if (classDecl == null) {
        log.debug("Failed to find class or interface declaration {}", testFilePath);
        continue;
      }

      final boolean hasContextConfig = classDecl.getAnnotations().stream()
          .map(AnnotationExpr::getNameAsString)
          .anyMatch(impactCheckerConfig.getAnnotations()::contains);

      if (!hasContextConfig) {
        log.debug("No matching annotation found for {}", testFilePath);
        continue;
      }

      final Set<String> causesForTest = new HashSet<>();
      final Set<String> onlyMockedChangedClasses = new HashSet<>();

      compilationUnit
          .findAll(MethodCallExpr.class)
          .forEach(
              mce -> {
                if ("mock".equals(mce.getNameAsString()) && mce.getArguments().size() == 1) {
                  final String arg = mce.getArgument(0).toString();
                  changedClassNames.forEach(
                      className -> {
                        if (arg.contains(className + ".class")) {
                          onlyMockedChangedClasses.add(className);
                        }
                      });
                }
              });

      compilationUnit
          .findAll(ClassOrInterfaceType.class)
          .forEach(
              type -> {
                final String typeName = type.getNameAsString();
                causesForTest.addAll(referencedTypeToChangedClasses.getOrDefault(typeName, Set.of()));
              });

      causesForTest.removeAll(onlyMockedChangedClasses);

      if (!causesForTest.isEmpty()) {
        relevantTestsWithCauses.put(testFilePath, causesForTest);
      }
    }

    return relevantTestsWithCauses;
  }

  /**
   * Finds the set of changed Java class file paths within the specified main Java
   * source
   * directories in a given Git repository.
   *
   * <p>
   * This method opens the Git repository located at {@code gitRepoPath},
   * determines the changed
   * files using {@code git diff}, and collects the relative paths (to the
   * repository root) of all
   * changed files that:
   *
   * <ul>
   * <li>Have a ".java" extension.
   * <li>Reside within any of the provided {@code mainJavaDirs} directories.
   * </ul>
   *
   * <p>
   * If the repository cannot be accessed or an error occurs, {@code null} is
   * returned.
   *
   * @param mainJavaDirs a collection of paths to main Java source directories
   *                     (e.g., {@code
   *     src/main/java})
   * @param gitRepoPath  the root path to the Git repository
   * @return a set of relative paths (to the repo root) of changed Java files
   *         within the specified
   *         directories, or {@code null} if the repository could not be accessed
   */
  public Set<Path> findChangedClassPaths(
      final Collection<Path> mainJavaDirs, final Path gitRepoPath) {
    final Set<Path> changedClassPath = new HashSet<>();

    log.debug("gitRepoPath: {}", gitRepoPath);
    final List<DiffEntry> diffs = GitImpactUtils.getDiffEntries(gitRepoPath, impactCheckerConfig);

    log.debug(
        "Found diffs {}",
        diffs.stream().map(DiffEntry::toString).collect(Collectors.joining(", ")));

    for (final DiffEntry diff : diffs) {
      final String currentDiffPath = diff.getNewPath();
      if (currentDiffPath.endsWith(".java")) {
        // Check if this file is under any src/main/java dir
        for (final Path mainDir : mainJavaDirs) {
          final Path mainDirAbs = mainDir.toAbsolutePath().normalize();
          final Path fileAbs = gitRepoPath.resolve(currentDiffPath).toAbsolutePath().normalize();
          if (fileAbs.startsWith(mainDirAbs)) {
            changedClassPath.add(gitRepoPath.toAbsolutePath().normalize().relativize(fileAbs));
          }
        }
      }
    }

    return changedClassPath;
  }

  /**
   * Finds the interfaces implemented by each of the specified changed Java class
   * files.
   *
   * <p>
   * For each path in {@code changedClassPaths}, this method parses the
   * corresponding Java source
   * file (resolved against {@code repoRoot}), identifies the first class
   * declaration (excluding
   * interfaces), and collects the simple names of all interfaces that the class
   * implements.
   *
   * <p>
   * The result is a map where each key is the relative class path and the value
   * is a set of
   * implemented interface names. Only classes that implement at least one
   * interface are included in
   * the result. Files that cannot be parsed or do not contain a class declaration
   * are ignored.
   *
   * @param changedClassPaths a collection of relative paths to changed Java class
   *                          files
   * @param repoRoot          the root path to the repository (used to resolve
   *                          class file paths)
   * @return a map from class file paths to sets of implemented interface names;
   *         classes without
   *         implemented interfaces are not included
   */
  public Map<Path, Set<String>> findImplementedInterfaces(
      final Collection<Path> changedClassPaths, final Path repoRoot) {
    final Map<Path, Set<String>> implementedInterfaces = new HashMap<>();
    for (final Path classPath : changedClassPaths) {
      final Path absPath = repoRoot.resolve(classPath);
      final ParseResult<CompilationUnit> compilationUnitParseResult = parseJavaSourceFile(absPath);
      if (compilationUnitParseResult == null
          || !compilationUnitParseResult.isSuccessful()
          || compilationUnitParseResult.getResult().isEmpty()) {
        continue;
      }

      final CompilationUnit cu = compilationUnitParseResult.getResult().get();
      final ClassOrInterfaceDeclaration classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
      if (classDecl == null || classDecl.isInterface()) {
        continue;
      }

      final Set<String> interfaces = classDecl.getImplementedTypes().stream()
          .map(ClassOrInterfaceType::getNameAsString)
          .collect(Collectors.toSet());
      if (!interfaces.isEmpty()) {
        implementedInterfaces.put(classPath, interfaces);
      }
    }

    return implementedInterfaces;
  }

  private Set<Path> getAllJavaFiles(final Path dir) {
    if (!Files.exists(dir)) {
      return Set.of();
    }

    try (final Stream<Path> paths = Files.walk(dir)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(p -> p.toString().endsWith(".java"))
          .collect(Collectors.toSet());
    } catch (final IOException e) {
      log.debug("Cannot process {}", dir, e);
      return Set.of();
    }
  }

  private ParseResult<CompilationUnit> parseJavaSourceFile(final Path jsf) {
    try (final FileInputStream in = FileUtils.openInputStream(jsf.toFile())) {
      final ParseResult<CompilationUnit> compilationUnitParseResult = javaParser.parse(in);
      if (!compilationUnitParseResult.isSuccessful()
          || compilationUnitParseResult.getResult().isEmpty()) {
        log.debug("No results parsing {}", jsf.toAbsolutePath());
      }

      return compilationUnitParseResult;
    } catch (final IOException e) {
      log.error("Failed to parse {}", jsf.toFile(), e);
      return null;
    }
  }

  private Map<String, Set<String>> buildReferencedTypeToChangedClasses(
      final Collection<Path> changedFilePaths, final Map<Path, Set<String>> implementedInterfaces) {
    final Map<String, Set<String>> referencedTypeToChangedClasses = new HashMap<>();
    changedFilePaths.forEach(
        changedFilePath -> {
          final String changedClassName = toSimpleClassName(changedFilePath);
          referencedTypeToChangedClasses.computeIfAbsent(changedClassName, key -> new HashSet<>())
              .add(changedClassName);
          implementedInterfaces.getOrDefault(changedFilePath, Set.of()).forEach(
              implementedInterface -> referencedTypeToChangedClasses.computeIfAbsent(
                  implementedInterface, key -> new HashSet<>()).add(changedClassName));
        });
    return referencedTypeToChangedClasses;
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
