package io.github.hillemacher.testimpactchecker.java.analysis;

import java.util.Set;

/**
 * Captures extracted usage metadata for a parsed test file.
 *
 * @param hasRequiredAnnotation whether the test has any configured impact annotation
 * @param referencedTypes simple type names referenced by the test
 */
public record TestTypeUsage(boolean hasRequiredAnnotation, Set<String> referencedTypes) {}
