package io.github.hillemacher.testimpactchecker.java.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import io.github.hillemacher.testimpactchecker.java.analysis.cache.JsonFileTypeIndexCache;
import io.github.hillemacher.testimpactchecker.java.analysis.cache.TypeIndexCache;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainSourceIndexBuilderCacheTest {

  @TempDir Path tempDir;

  /** Wraps a real cache and counts store() calls — each call represents a re-parsed file. */
  private static class CountingCache implements TypeIndexCache {
    private final TypeIndexCache delegate;
    final AtomicInteger storeCalls = new AtomicInteger();

    CountingCache(final TypeIndexCache delegate) {
      this.delegate = delegate;
    }

    @Override
    public Optional<io.github.hillemacher.testimpactchecker.java.analysis.cache.IndexEntry> lookup(
        final Path file, final String contentHash) {
      return delegate.lookup(file, contentHash);
    }

    @Override
    public void store(
        final Path file,
        final String contentHash,
        final io.github.hillemacher.testimpactchecker.java.analysis.cache.IndexEntry entry) {
      storeCalls.incrementAndGet();
      delegate.store(file, contentHash, entry);
    }

    @Override
    public void retainOnly(final Set<Path> observedFiles) {
      delegate.retainOnly(observedFiles);
    }

    @Override
    public void persist() throws IOException {
      delegate.persist();
    }
  }

  @Test
  void firstRunPopulatesCacheSecondRunReadsFromIt() throws IOException {
    final Path sources = tempDir.resolve("sources");
    Files.createDirectories(sources);
    writeClass(sources, "Alpha", "class Alpha { Beta b; }");
    writeClass(sources, "Beta", "class Beta {}");

    final Path cacheFile = tempDir.resolve("cache.json");
    final CountingCache firstCache = new CountingCache(new JsonFileTypeIndexCache(cacheFile));
    final MainSourceIndexBuilder firstBuilder = new MainSourceIndexBuilder(newParser(), firstCache);

    final TypeDependencyIndex first = firstBuilder.build(Set.of(sources));
    assertThat(first.forwardDependencies().get("Alpha")).contains("Beta");
    assertThat(firstCache.storeCalls.get()).isEqualTo(2);

    final CountingCache secondCache = new CountingCache(new JsonFileTypeIndexCache(cacheFile));
    final MainSourceIndexBuilder secondBuilder =
        new MainSourceIndexBuilder(newParser(), secondCache);
    final TypeDependencyIndex second = secondBuilder.build(Set.of(sources));

    assertThat(secondCache.storeCalls.get()).isZero();
    assertThat(second.forwardDependencies()).isEqualTo(first.forwardDependencies());
    assertThat(second.reverseDependencies()).isEqualTo(first.reverseDependencies());
  }

  @Test
  void editingOneFileTriggersReparseOfOnlyThatFile() throws IOException {
    final Path sources = tempDir.resolve("sources");
    Files.createDirectories(sources);
    writeClass(sources, "Alpha", "class Alpha { Beta b; }");
    writeClass(sources, "Beta", "class Beta {}");

    final Path cacheFile = tempDir.resolve("cache.json");
    new MainSourceIndexBuilder(newParser(), new JsonFileTypeIndexCache(cacheFile))
        .build(Set.of(sources));

    writeClass(sources, "Alpha", "class Alpha { Beta b; Gamma g; }");
    writeClass(sources, "Gamma", "class Gamma {}");

    final CountingCache cache = new CountingCache(new JsonFileTypeIndexCache(cacheFile));
    final TypeDependencyIndex index =
        new MainSourceIndexBuilder(newParser(), cache).build(Set.of(sources));

    assertThat(cache.storeCalls.get()).isEqualTo(2);
    assertThat(index.forwardDependencies().get("Alpha")).contains("Beta", "Gamma");
    assertThat(index.forwardDependencies()).containsKey("Gamma");
  }

  @Test
  void deletedFilesAreDroppedFromCache() throws IOException {
    final Path sources = tempDir.resolve("sources");
    Files.createDirectories(sources);
    writeClass(sources, "Alpha", "class Alpha {}");
    writeClass(sources, "Beta", "class Beta {}");

    final Path cacheFile = tempDir.resolve("cache.json");
    new MainSourceIndexBuilder(newParser(), new JsonFileTypeIndexCache(cacheFile))
        .build(Set.of(sources));
    assertThat(new JsonFileTypeIndexCache(cacheFile).size()).isEqualTo(2);

    Files.delete(sources.resolve("Beta.java"));
    new MainSourceIndexBuilder(newParser(), new JsonFileTypeIndexCache(cacheFile))
        .build(Set.of(sources));

    assertThat(new JsonFileTypeIndexCache(cacheFile).size()).isEqualTo(1);
  }

  private static JavaParser newParser() {
    final ParserConfiguration config = new ParserConfiguration();
    config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    return new JavaParser(config);
  }

  private static void writeClass(final Path sources, final String name, final String body)
      throws IOException {
    Files.writeString(sources.resolve(name + ".java"), body, StandardCharsets.UTF_8);
  }
}
