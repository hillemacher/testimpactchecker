package io.github.hillemacher.testimpactchecker.java.analysis;

import java.util.Map;
import java.util.Set;

/**
 * Index of type relationships discovered in main source code.
 *
 * @param forwardDependencies owner type to referenced types
 * @param reverseDependencies referenced type to owner/dependent types
 * @param typeDefinitionIndex simple type name to declaring file paths
 */
public record TypeDependencyIndex(
    Map<String, Set<String>> forwardDependencies,
    Map<String, Set<String>> reverseDependencies,
    Map<String, Set<String>> typeDefinitionIndex) {
}
