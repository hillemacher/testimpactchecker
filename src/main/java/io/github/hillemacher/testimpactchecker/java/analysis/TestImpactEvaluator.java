package io.github.hillemacher.testimpactchecker.java.analysis;

import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestImpactEvaluator {

  /**
   * Computes changed-class causes for a test from referenced types and mock policy.
   *
   * <p>The evaluator first resolves candidate causes from referenced impacted types, then applies
   * configured mock filtering:
   * <ul>
   * <li>{@link io.github.hillemacher.testimpactchecker.config.MockPolicy#CURRENT}: removes directly mocked changed classes</li>
   * <li>{@link io.github.hillemacher.testimpactchecker.config.MockPolicy#FILTER_MOCKED_PATHS}: keeps a cause only if at least one witness path remains unblocked by mocked types</li>
   * </ul>
   *
   * @param referencedTypes types referenced by the test source
   * @param mockedTypes types mocked by the test source
   * @param changedClassNames changed class simple names
   * @param impactedTypeToCauses map of impacted type to root changed-class causes
   * @param witnessPathsByTypeAndCause witness dependency paths per impacted type and cause
   * @param mockPolicy mock filtering policy
   * @return surviving changed-class causes for the test after applying mock policy
   */
  public Set<String> evaluateCauses(
      final Set<String> referencedTypes,
      final Set<String> mockedTypes,
      final Set<String> changedClassNames,
      final Map<String, Set<String>> impactedTypeToCauses,
      final Map<String, Map<String, Set<List<String>>>> witnessPathsByTypeAndCause,
      final MockPolicy mockPolicy) {

    final Set<String> causes = new HashSet<>();
    for (final String referencedType : referencedTypes) {
      causes.addAll(impactedTypeToCauses.getOrDefault(referencedType, Set.of()));
    }

    if (causes.isEmpty()) {
      return causes;
    }

    if (mockPolicy == MockPolicy.CURRENT) {
      final Set<String> mockedChangedClasses = new HashSet<>(mockedTypes);
      mockedChangedClasses.retainAll(changedClassNames);
      causes.removeAll(mockedChangedClasses);
      return causes;
    }

    if (mockPolicy == MockPolicy.FILTER_MOCKED_PATHS) {
      return filterCausesByWitnessPaths(referencedTypes, mockedTypes, causes, witnessPathsByTypeAndCause);
    }

    return causes;
  }

  private Set<String> filterCausesByWitnessPaths(
      final Set<String> referencedTypes,
      final Set<String> mockedTypes,
      final Set<String> causes,
      final Map<String, Map<String, Set<List<String>>>> witnessPathsByTypeAndCause) {
    final Set<String> survivingCauses = new HashSet<>();

    for (final String cause : causes) {
      boolean hasUnblockedPath = false;
      for (final String referencedType : referencedTypes) {
        final Set<List<String>> witnessPaths = witnessPathsByTypeAndCause
            .getOrDefault(referencedType, Map.of())
            .getOrDefault(cause, Set.of());
        if (witnessPaths.isEmpty()) {
          continue;
        }

        final boolean anyUnblocked = witnessPaths.stream()
            .anyMatch(path -> path.stream().noneMatch(mockedTypes::contains));
        if (anyUnblocked) {
          hasUnblockedPath = true;
          break;
        }
      }

      if (hasUnblockedPath) {
        survivingCauses.add(cause);
      }
    }

    return survivingCauses;
  }
}
