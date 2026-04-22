package io.github.hillemacher.testimpactchecker.java.analysis.cache;

import java.util.Set;

/**
 * Cacheable per-file parse result for main-source index construction.
 *
 * @param filePath absolute path of the parsed Java file
 * @param ownerType simple name of the primary declared type, or {@code null} when the file has no
 *     class-or-interface declaration (e.g. {@code package-info.java})
 * @param referencedTypes simple names of types referenced by the parsed file, excluding {@code
 *     ownerType} itself
 */
public record IndexEntry(String filePath, String ownerType, Set<String> referencedTypes) {}
