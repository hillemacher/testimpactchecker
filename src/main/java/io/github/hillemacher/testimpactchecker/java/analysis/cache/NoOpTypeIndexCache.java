package io.github.hillemacher.testimpactchecker.java.analysis.cache;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/** No-op cache: every lookup misses and nothing is persisted. Used when caching is disabled. */
public class NoOpTypeIndexCache implements TypeIndexCache {

  @Override
  public Optional<IndexEntry> lookup(final Path file, final String contentHash) {
    return Optional.empty();
  }

  @Override
  public void store(final Path file, final String contentHash, final IndexEntry entry) {
    // no-op
  }

  @Override
  public void retainOnly(final Set<Path> observedFiles) {
    // no-op
  }

  @Override
  public void persist() {
    // no-op
  }
}
