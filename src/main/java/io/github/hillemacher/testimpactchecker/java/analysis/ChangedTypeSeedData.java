package io.github.hillemacher.testimpactchecker.java.analysis;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates seed inputs derived from changed classes.
 *
 * @param implementedInterfacesByClass changed class path to directly implemented interfaces
 * @param seedTypeToChangedClasses seed type name to root changed-class causes
 * @param changedClassNames simple names of changed classes
 */
public record ChangedTypeSeedData(
    Map<Path, Set<String>> implementedInterfacesByClass,
    Map<String, Set<String>> seedTypeToChangedClasses,
    Set<String> changedClassNames) {}
