package io.github.hillemacher.testimpactchecker.java.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import io.github.hillemacher.testimpactchecker.java.analysis.cache.FileHasher;
import io.github.hillemacher.testimpactchecker.java.analysis.cache.IndexEntry;
import io.github.hillemacher.testimpactchecker.java.analysis.cache.NoOpTypeIndexCache;
import io.github.hillemacher.testimpactchecker.java.analysis.cache.TypeIndexCache;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainSourceIndexBuilder {

  private final JavaParser javaParser;
  private final TypeIndexCache cache;

  /** Creates a builder with caching disabled (no-op cache). */
  public MainSourceIndexBuilder(final JavaParser javaParser) {
    this(javaParser, new NoOpTypeIndexCache());
  }

  public MainSourceIndexBuilder(final JavaParser javaParser, final TypeIndexCache cache) {
    this.javaParser = javaParser;
    this.cache = cache;
  }

  /**
   * Builds forward and reverse simple-name dependency indexes for main source types.
   *
   * <p>The builder parses each Java type in discovered main-source directories and records:
   *
   * <ul>
   *   <li>forward dependencies: owner type -&gt; referenced types
   *   <li>reverse dependencies: referenced type -&gt; dependent owner types
   *   <li>type definitions: type name -&gt; declaring file paths
   * </ul>
   *
   * When a {@link TypeIndexCache} other than the no-op is configured, unchanged files are served
   * from cache instead of being re-parsed.
   *
   * @param mainJavaDirs directories containing main Java source files
   * @return dependency index with forward, reverse, and definition lookups by simple type name
   */
  public TypeDependencyIndex build(final Set<Path> mainJavaDirs) {
    final List<IndexEntry> entries = new ArrayList<>();
    final Set<Path> observedFiles = new HashSet<>();

    for (final Path mainJavaDir : mainJavaDirs) {
      for (final Path javaFile : getAllJavaFiles(mainJavaDir)) {
        observedFiles.add(javaFile);
        parseWithCache(javaFile).ifPresent(entries::add);
      }
    }

    cache.retainOnly(observedFiles);
    try {
      cache.persist();
    } catch (final IOException e) {
      log.warn("Failed to persist type index cache", e);
    }

    return aggregate(entries);
  }

  /**
   * Pure aggregation over parsed entries — separated for testability and cache round-trips.
   *
   * @param entries parsed entries from the main-source scan
   * @return dependency index derived from the entries
   */
  public static TypeDependencyIndex aggregate(final Collection<IndexEntry> entries) {
    final Map<String, Set<String>> forwardDependencies = new HashMap<>();
    final Map<String, Set<String>> reverseDependencies = new HashMap<>();
    final Map<String, Set<String>> typeDefinitionIndex = new HashMap<>();

    for (final IndexEntry entry : entries) {
      if (entry.ownerType() == null) {
        continue;
      }
      final String ownerType = entry.ownerType();
      typeDefinitionIndex.computeIfAbsent(ownerType, key -> new HashSet<>()).add(entry.filePath());
      final Set<String> referencedTypes =
          entry.referencedTypes() == null ? Set.of() : entry.referencedTypes();
      forwardDependencies
          .computeIfAbsent(ownerType, key -> new HashSet<>())
          .addAll(referencedTypes);
      referencedTypes.forEach(
          referencedType ->
              reverseDependencies
                  .computeIfAbsent(referencedType, key -> new HashSet<>())
                  .add(ownerType));
    }

    return new TypeDependencyIndex(forwardDependencies, reverseDependencies, typeDefinitionIndex);
  }

  private Optional<IndexEntry> parseWithCache(final Path javaFile) {
    final String contentHash;
    try {
      contentHash = FileHasher.sha256(javaFile);
    } catch (final IOException e) {
      log.warn("Failed to hash {}; falling back to direct parse", javaFile, e);
      return parseJavaSourceFile(javaFile);
    }

    final Optional<IndexEntry> cached = cache.lookup(javaFile, contentHash);
    if (cached.isPresent()) {
      return cached;
    }

    final Optional<IndexEntry> parsed = parseJavaSourceFile(javaFile);
    parsed.ifPresent(entry -> cache.store(javaFile, contentHash, entry));
    return parsed;
  }

  private Optional<IndexEntry> parseJavaSourceFile(final Path javaFile) {
    final ParseResult<CompilationUnit> parseResult;
    try (final FileInputStream in = new FileInputStream(javaFile.toFile())) {
      parseResult = javaParser.parse(in);
    } catch (final IOException ex) {
      log.error("Failed to parse {}", javaFile, ex);
      return Optional.empty();
    }

    if (parseResult == null || !parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
      return Optional.empty();
    }

    final CompilationUnit compilationUnit = parseResult.getResult().get();
    final ClassOrInterfaceDeclaration ownerDecl =
        compilationUnit.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
    if (ownerDecl == null) {
      return Optional.of(new IndexEntry(javaFile.toString(), null, Set.of()));
    }

    final String ownerType = ownerDecl.getNameAsString();
    final Set<String> referencedTypes = new TreeSet<>();
    for (final ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
      final String name = type.getNameAsString();
      if (!ownerType.equals(name)) {
        referencedTypes.add(name);
      }
    }
    return Optional.of(new IndexEntry(javaFile.toString(), ownerType, referencedTypes));
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
}
