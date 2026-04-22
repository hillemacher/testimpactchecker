package io.github.hillemacher.testimpactchecker.java.analysis.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileTypeIndexCacheTest {

  @TempDir Path tempDir;

  @Test
  void lookupMissesWhenCacheFileDoesNotExist() {
    final JsonFileTypeIndexCache cache = new JsonFileTypeIndexCache(tempDir.resolve("cache.json"));
    assertThat(cache.lookup(tempDir.resolve("Foo.java"), "abc")).isEmpty();
  }

  @Test
  void storeThenLookupReturnsSameEntryWhenHashMatches() throws IOException {
    final Path cacheFile = tempDir.resolve("cache.json");
    final JsonFileTypeIndexCache cache = new JsonFileTypeIndexCache(cacheFile);
    final Path file = tempDir.resolve("Foo.java");
    final IndexEntry entry = new IndexEntry(file.toString(), "Foo", Set.of("Bar", "Baz"));

    cache.store(file, "hash-1", entry);
    cache.persist();

    final JsonFileTypeIndexCache reloaded = new JsonFileTypeIndexCache(cacheFile);
    final Optional<IndexEntry> hit = reloaded.lookup(file, "hash-1");
    assertThat(hit).isPresent();
    assertThat(hit.get().ownerType()).isEqualTo("Foo");
    assertThat(hit.get().referencedTypes()).containsExactlyInAnyOrder("Bar", "Baz");
  }

  @Test
  void lookupMissesWhenHashDiffers() {
    final JsonFileTypeIndexCache cache = new JsonFileTypeIndexCache(tempDir.resolve("cache.json"));
    final Path file = tempDir.resolve("Foo.java");
    cache.store(file, "hash-1", new IndexEntry(file.toString(), "Foo", Set.of()));

    assertThat(cache.lookup(file, "hash-2")).isEmpty();
  }

  @Test
  void retainOnlyDropsMissingEntries() throws IOException {
    final Path cacheFile = tempDir.resolve("cache.json");
    final JsonFileTypeIndexCache cache = new JsonFileTypeIndexCache(cacheFile);
    final Path a = tempDir.resolve("A.java");
    final Path b = tempDir.resolve("B.java");
    cache.store(a, "h", new IndexEntry(a.toString(), "A", Set.of()));
    cache.store(b, "h", new IndexEntry(b.toString(), "B", Set.of()));
    assertThat(cache.size()).isEqualTo(2);

    cache.retainOnly(Set.of(a));
    assertThat(cache.size()).isEqualTo(1);
    assertThat(cache.lookup(b, "h")).isEmpty();

    cache.persist();
    assertThat(new JsonFileTypeIndexCache(cacheFile).size()).isEqualTo(1);
  }

  @Test
  void versionMismatchIsTreatedAsEmpty() throws IOException {
    final Path cacheFile = tempDir.resolve("cache.json");
    Files.writeString(
        cacheFile,
        "{\"cacheVersion\":999,\"entries\":[{\"filePath\":\"x\",\"contentHash\":\"h\","
            + "\"ownerType\":\"X\",\"referencedTypes\":[]}]}",
        StandardCharsets.UTF_8);

    final JsonFileTypeIndexCache cache = new JsonFileTypeIndexCache(cacheFile);
    assertThat(cache.size()).isZero();
    assertThat(cache.lookup(Path.of("x"), "h")).isEmpty();
  }

  @Test
  void persistedFileIncludesVersionField() throws IOException {
    final Path cacheFile = tempDir.resolve("cache.json");
    final JsonFileTypeIndexCache cache = new JsonFileTypeIndexCache(cacheFile);
    cache.store(
        tempDir.resolve("Foo.java"),
        "h",
        new IndexEntry(tempDir.resolve("Foo.java").toString(), "Foo", Set.of()));
    cache.persist();

    final String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
    assertThat(json).contains("\"cacheVersion\" : " + JsonFileTypeIndexCache.CACHE_VERSION);
    assertThat(json).contains("\"ownerType\" : \"Foo\"");
  }
}
