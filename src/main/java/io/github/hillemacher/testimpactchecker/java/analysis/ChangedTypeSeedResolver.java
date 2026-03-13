package io.github.hillemacher.testimpactchecker.java.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@RequiredArgsConstructor
@Slf4j
public class ChangedTypeSeedResolver {

  private final JavaParser javaParser;

  /**
   * Builds the initial seed model required by impact propagation.
   *
   * <p>For each changed class this method collects:
   *
   * <ul>
   *   <li>the changed class simple name as a direct seed
   *   <li>all directly implemented interface simple names as additional seeds
   * </ul>
   *
   * The method also computes the set of changed-class root causes used later for reporting. This
   * keeps seed extraction logic isolated from traversal and test-evaluation concerns.
   *
   * @param changedClassPaths changed class file paths relative to repository root
   * @param repoRoot repository root path used to resolve changed class files
   * @return aggregated seed data for direct and transitive impact analysis stages
   */
  public ChangedTypeSeedData resolve(
      final Collection<Path> changedClassPaths, final Path repoRoot) {
    final Map<Path, Set<String>> implementedInterfaces =
        findImplementedInterfaces(changedClassPaths, repoRoot);
    final Map<String, Set<String>> seedTypeToChangedClasses =
        buildSeedTypeToChangedClasses(changedClassPaths, implementedInterfaces);
    final Set<String> changedClassNames =
        changedClassPaths.stream().map(this::toSimpleClassName).collect(Collectors.toSet());
    return new ChangedTypeSeedData(
        implementedInterfaces, seedTypeToChangedClasses, changedClassNames);
  }

  /**
   * Resolves directly implemented interfaces for each changed class source file.
   *
   * <p>Only concrete classes are considered; interface declarations and unparsable files are
   * ignored. The returned values are simple type names to align with the current name-matching
   * strategy used throughout the analyzer.
   *
   * @param changedClassPaths changed class file paths relative to repository root
   * @param repoRoot repository root path used to resolve changed class files
   * @return map from changed class path to directly implemented interface simple names
   */
  public Map<Path, Set<String>> findImplementedInterfaces(
      final Collection<Path> changedClassPaths, final Path repoRoot) {
    final Map<Path, Set<String>> implementedInterfaces = new HashMap<>();
    for (final Path classPath : changedClassPaths) {
      final Path absPath = repoRoot.resolve(classPath);
      final ParseResult<CompilationUnit> parseResult = parseJavaSourceFile(absPath);
      if (parseResult == null || !parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
        continue;
      }

      final CompilationUnit compilationUnit = parseResult.getResult().get();
      final ClassOrInterfaceDeclaration classDecl =
          compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
      if (classDecl == null || classDecl.isInterface()) {
        continue;
      }

      final Set<String> interfaces =
          classDecl.getImplementedTypes().stream()
              .map(ClassOrInterfaceType::getNameAsString)
              .collect(Collectors.toSet());
      if (!interfaces.isEmpty()) {
        implementedInterfaces.put(classPath, interfaces);
      }
    }

    return implementedInterfaces;
  }

  private Map<String, Set<String>> buildSeedTypeToChangedClasses(
      final Collection<Path> changedFilePaths, final Map<Path, Set<String>> implementedInterfaces) {
    final Map<String, Set<String>> seedTypeToChangedClasses = new HashMap<>();
    changedFilePaths.forEach(
        changedFilePath -> {
          final String changedClassName = toSimpleClassName(changedFilePath);
          seedTypeToChangedClasses
              .computeIfAbsent(changedClassName, key -> new HashSet<>())
              .add(changedClassName);
          implementedInterfaces
              .getOrDefault(changedFilePath, Set.of())
              .forEach(
                  implementedInterface ->
                      seedTypeToChangedClasses
                          .computeIfAbsent(implementedInterface, key -> new HashSet<>())
                          .add(changedClassName));
        });
    return seedTypeToChangedClasses;
  }

  private ParseResult<CompilationUnit> parseJavaSourceFile(final Path javaSourceFile) {
    try (final FileInputStream in = FileUtils.openInputStream(javaSourceFile.toFile())) {
      return javaParser.parse(in);
    } catch (final IOException e) {
      log.error("Failed to parse {}", javaSourceFile.toFile(), e);
      return null;
    }
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
