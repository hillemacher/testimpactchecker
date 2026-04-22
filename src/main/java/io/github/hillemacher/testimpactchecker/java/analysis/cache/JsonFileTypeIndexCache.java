package io.github.hillemacher.testimpactchecker.java.analysis.cache;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;

/**
 * File-backed {@link TypeIndexCache} that persists per-file parse results as JSON.
 *
 * <p>The on-disk layout is versioned via a {@code cacheVersion} field. A mismatched version or
 * unreadable file is treated as an empty cache so the next run rebuilds from scratch. Paths are
 * stored as strings normalized with forward slashes for cross-platform reproducibility.
 */
@Slf4j
public class JsonFileTypeIndexCache implements TypeIndexCache {

  public static final int CACHE_VERSION = 1;

  private final Path cacheFile;
  private final ObjectMapper objectMapper;
  private final Map<String, CacheEntryDto> entries = new HashMap<>();

  public JsonFileTypeIndexCache(final Path cacheFile) {
    this.cacheFile = cacheFile;
    this.objectMapper =
        new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.CLOSE_CLOSEABLE);
    this.objectMapper.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    load();
  }

  @Override
  public Optional<IndexEntry> lookup(final Path file, final String contentHash) {
    final CacheEntryDto dto = entries.get(normalizeKey(file));
    if (dto == null || !contentHash.equals(dto.contentHash())) {
      return Optional.empty();
    }
    return Optional.of(
        new IndexEntry(
            dto.filePath(),
            dto.ownerType(),
            dto.referencedTypes() == null ? Set.of() : new TreeSet<>(dto.referencedTypes())));
  }

  @Override
  public void store(final Path file, final String contentHash, final IndexEntry entry) {
    final List<String> referencedTypes =
        entry.referencedTypes() == null
            ? List.of()
            : new ArrayList<>(new TreeSet<>(entry.referencedTypes()));
    entries.put(
        normalizeKey(file),
        new CacheEntryDto(entry.filePath(), contentHash, entry.ownerType(), referencedTypes));
  }

  @Override
  public void retainOnly(final Set<Path> observedFiles) {
    final Set<String> keep = new java.util.HashSet<>();
    observedFiles.forEach(p -> keep.add(normalizeKey(p)));
    entries.keySet().retainAll(keep);
  }

  @Override
  public void persist() throws IOException {
    if (cacheFile.getParent() != null) {
      Files.createDirectories(cacheFile.getParent());
    }
    final List<CacheEntryDto> sorted = new ArrayList<>(entries.values());
    sorted.sort(Comparator.comparing(CacheEntryDto::filePath));
    final CacheFileDto file = new CacheFileDto(CACHE_VERSION, sorted);
    try (final var out = Files.newBufferedWriter(cacheFile)) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, file);
    }
  }

  /** Number of cached entries currently loaded; primarily intended for diagnostics and tests. */
  public int size() {
    return entries.size();
  }

  private void load() {
    if (!Files.isRegularFile(cacheFile)) {
      return;
    }
    try (final var in = Files.newBufferedReader(cacheFile)) {
      final CacheFileDto dto = objectMapper.readValue(in, CacheFileDto.class);
      if (dto == null || dto.cacheVersion() != CACHE_VERSION || dto.entries() == null) {
        log.info(
            "Ignoring cache file {} (version mismatch or malformed); a full rebuild will run",
            cacheFile);
        return;
      }
      dto.entries().forEach(entry -> entries.put(normalizeKey(Path.of(entry.filePath())), entry));
    } catch (final IOException e) {
      log.warn("Failed to read cache file {}; rebuilding", cacheFile, e);
      entries.clear();
    }
  }

  private static String normalizeKey(final Path file) {
    return file.toAbsolutePath().normalize().toString().replace('\\', '/');
  }

  @JsonPropertyOrder({"cacheVersion", "entries"})
  record CacheFileDto(int cacheVersion, List<CacheEntryDto> entries) {}

  @JsonPropertyOrder({"filePath", "contentHash", "ownerType", "referencedTypes"})
  record CacheEntryDto(
      String filePath, String contentHash, String ownerType, List<String> referencedTypes) {}
}
