package io.github.hillemacher.testimpactchecker.java.analysis.cache;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Persistent store for per-file parse results keyed by file path and content hash.
 *
 * <p>A cache hit avoids re-parsing an unchanged Java file. Implementations must invalidate entries
 * whose recorded hash does not match the current content hash.
 */
public interface TypeIndexCache {

  /**
   * Returns the cached entry when the stored content hash matches {@code contentHash}.
   *
   * @param file absolute path of the Java file
   * @param contentHash current content fingerprint of the file
   * @return cached entry when hashes match, otherwise {@link Optional#empty()}
   */
  Optional<IndexEntry> lookup(Path file, String contentHash);

  /**
   * Stores {@code entry} against {@code file} keyed by {@code contentHash}.
   *
   * @param file absolute path of the Java file
   * @param contentHash current content fingerprint of the file
   * @param entry parsed entry for the file
   */
  void store(Path file, String contentHash, IndexEntry entry);

  /**
   * Drops cached entries whose file paths are not present in {@code observedFiles}, typically to
   * remove entries for deleted or moved files.
   */
  void retainOnly(Set<Path> observedFiles);

  /** Persists any pending changes to disk; a no-op for in-memory implementations. */
  void persist() throws IOException;
}
