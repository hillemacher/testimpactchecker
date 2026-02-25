package io.github.hillemacher.testimpactchecker.java.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class MainSourceIndexBuilder {

  private final JavaParser javaParser;

  /**
   * Builds forward and reverse simple-name dependency indexes for main source types.
   *
   * <p>The builder parses each Java type in discovered main-source directories and records:
   * <ul>
   * <li>forward dependencies: owner type -&gt; referenced types</li>
   * <li>reverse dependencies: referenced type -&gt; dependent owner types</li>
   * <li>type definitions: type name -&gt; declaring file paths</li>
   * </ul>
   * The resulting index is consumed by transitive propagation and diagnostics.
   *
   * @param mainJavaDirs directories containing main Java source files
   * @return dependency index with forward, reverse, and definition lookups by simple type name
   */
  public TypeDependencyIndex build(final Set<Path> mainJavaDirs) {
    final Map<String, Set<String>> forwardDependencies = new HashMap<>();
    final Map<String, Set<String>> reverseDependencies = new HashMap<>();
    final Map<String, Set<String>> typeDefinitionIndex = new HashMap<>();

    for (final Path mainJavaDir : mainJavaDirs) {
      for (final Path javaFile : getAllJavaFiles(mainJavaDir)) {
        final ParseResult<CompilationUnit> parseResult = parseJavaSourceFile(javaFile);
        if (parseResult == null || !parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
          continue;
        }

        final CompilationUnit compilationUnit = parseResult.getResult().get();
        final ClassOrInterfaceDeclaration ownerDecl =
            compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
        if (ownerDecl == null) {
          continue;
        }

        final String ownerType = ownerDecl.getNameAsString();
        typeDefinitionIndex.computeIfAbsent(ownerType, key -> new HashSet<>()).add(javaFile.toString());

        final Set<String> referencedTypes = compilationUnit.findAll(ClassOrInterfaceType.class).stream()
            .map(ClassOrInterfaceType::getNameAsString)
            .filter(type -> !ownerType.equals(type))
            .collect(java.util.stream.Collectors.toSet());

        forwardDependencies.computeIfAbsent(ownerType, key -> new HashSet<>()).addAll(referencedTypes);
        referencedTypes.forEach(referencedType -> reverseDependencies
            .computeIfAbsent(referencedType, key -> new HashSet<>())
            .add(ownerType));
      }
    }

    return new TypeDependencyIndex(forwardDependencies, reverseDependencies, typeDefinitionIndex);
  }

  private Set<Path> getAllJavaFiles(final Path dir) {
    if (!Files.exists(dir)) {
      return Set.of();
    }

    try (final Stream<Path> paths = Files.walk(dir)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .collect(java.util.stream.Collectors.toSet());
    } catch (final IOException e) {
      log.debug("Cannot process {}", dir, e);
      return Set.of();
    }
  }

  private ParseResult<CompilationUnit> parseJavaSourceFile(final Path javaSourceFile) {
    try (final FileInputStream in = new FileInputStream(javaSourceFile.toFile())) {
      return javaParser.parse(in);
    } catch (final IOException ex) {
      log.error("Failed to parse {}", javaSourceFile, ex);
      return null;
    }
  }
}
