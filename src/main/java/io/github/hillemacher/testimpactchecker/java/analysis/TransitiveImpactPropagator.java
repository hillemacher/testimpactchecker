package io.github.hillemacher.testimpactchecker.java.analysis;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NonNull;

public class TransitiveImpactPropagator {

  /**
   * Propagates changed-class causes through reverse dependencies up to {@code maxDepth}.
   *
   * <p>Traversal is breadth-first per seed/cause pair. Each visited type records:
   * <ul>
   * <li>its root changed-class causes</li>
   * <li>witness paths from impacted type back to seed type</li>
   * </ul>
   * Witness paths are later used by mock-aware filtering to suppress impacts that are fully
   * blocked by mocked intermediate types in a test.
   *
   * @param seedTypeToChangedClasses map of seed type to root changed-class causes
   * @param reverseDependencies reverse dependency graph (referenced type -> dependent types)
   * @param maxDepth maximum number of dependency hops to traverse from each seed
   * @return propagated impacted types with root causes and witness paths
   */
  public ImpactPropagationResult propagate(
      final Map<String, Set<String>> seedTypeToChangedClasses,
      final Map<String, Set<String>> reverseDependencies,
      final int maxDepth) {
    final Map<String, Set<String>> impactedTypeToCauses = new HashMap<>();
    final Map<String, Map<String, Set<List<String>>>> witnessPathsByTypeAndCause = new HashMap<>();

    for (final Map.Entry<String, Set<String>> seedEntry : seedTypeToChangedClasses.entrySet()) {
      final String seedType = seedEntry.getKey();
      final Set<String> changedClassCauses = seedEntry.getValue();

      for (final String cause : changedClassCauses) {
        final ArrayDeque<TraversalNode> queue = new ArrayDeque<>();
        final Map<String, Integer> bestDepthByType = new HashMap<>();
        queue.add(new TraversalNode(seedType, 0, List.of(seedType)));
        bestDepthByType.put(seedType, 0);

        while (!queue.isEmpty()) {
          final TraversalNode node = queue.removeFirst();
          final String currentType = node.type();

          impactedTypeToCauses.computeIfAbsent(currentType, key -> new HashSet<>()).add(cause);
          witnessPathsByTypeAndCause
              .computeIfAbsent(currentType, key -> new HashMap<>())
              .computeIfAbsent(cause, key -> new LinkedHashSet<>())
              .add(node.pathFromCurrentToSeed());

          if (node.depth() >= maxDepth) {
            continue;
          }

          for (final String dependentType : reverseDependencies.getOrDefault(currentType, Set.of())) {
            final int nextDepth = node.depth() + 1;
            final Integer bestDepth = bestDepthByType.get(dependentType);
            if (bestDepth != null && bestDepth < nextDepth) {
              continue;
            }

            final List<String> nextPath = prependType(dependentType, node.pathFromCurrentToSeed());
            queue.add(new TraversalNode(dependentType, nextDepth, nextPath));
            bestDepthByType.put(dependentType, nextDepth);
          }
        }
      }
    }

    return new ImpactPropagationResult(impactedTypeToCauses, witnessPathsByTypeAndCause);
  }

  private List<String> prependType(final String type, final List<String> existingPath) {
    final List<String> path = new java.util.ArrayList<>(existingPath.size() + 1);
    path.add(type);
    path.addAll(existingPath);
    return List.copyOf(path);
  }

  private record TraversalNode(@NonNull String type, int depth, @NonNull List<String> pathFromCurrentToSeed) {
  }
}
