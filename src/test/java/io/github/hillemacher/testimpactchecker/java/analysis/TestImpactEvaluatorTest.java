package io.github.hillemacher.testimpactchecker.java.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TestImpactEvaluator} mock policy filtering logic. */
class TestImpactEvaluatorTest {

  private final TestImpactEvaluator evaluator = new TestImpactEvaluator();

  /** Empty referenced types must short-circuit to an empty cause set. */
  @Test
  void emptyReferencedTypesReturnsEmptyCauses() {
    final Set<String> causes =
        evaluator.evaluateCauses(
            Set.of(),
            Set.of(),
            Set.of("ServiceA"),
            Map.of("ServiceA", Set.of("ServiceA")),
            Map.of("ServiceA", Map.of("ServiceA", Set.of(List.of("ServiceA")))),
            MockPolicy.CURRENT);

    assertThat(causes).isEmpty();
  }

  /** CURRENT policy with no mocks must keep every cause referenced by the test. */
  @Test
  void currentPolicyWithoutMocksReturnsAllCauses() {
    final Set<String> causes =
        evaluator.evaluateCauses(
            Set.of("ServiceA"),
            Set.of(),
            Set.of("ServiceA"),
            Map.of("ServiceA", Set.of("ServiceA")),
            Map.of("ServiceA", Map.of("ServiceA", Set.of(List.of("ServiceA")))),
            MockPolicy.CURRENT);

    assertThat(causes).containsExactly("ServiceA");
  }

  /** CURRENT policy must drop a cause when the changed class itself is mocked by the test. */
  @Test
  void currentPolicyDropsCauseWhenChangedClassIsMocked() {
    final Set<String> causes =
        evaluator.evaluateCauses(
            Set.of("ServiceA"),
            Set.of("ServiceA"),
            Set.of("ServiceA"),
            Map.of("ServiceA", Set.of("ServiceA")),
            Map.of("ServiceA", Map.of("ServiceA", Set.of(List.of("ServiceA")))),
            MockPolicy.CURRENT);

    assertThat(causes).isEmpty();
  }

  /** CURRENT policy must keep a cause when the mocked type is not among changed classes. */
  @Test
  void currentPolicyKeepsCauseWhenMockedTypeNotInChangedClasses() {
    final Set<String> causes =
        evaluator.evaluateCauses(
            Set.of("ServiceA"),
            Set.of("Logger"),
            Set.of("ServiceA"),
            Map.of("ServiceA", Set.of("ServiceA")),
            Map.of("ServiceA", Map.of("ServiceA", Set.of(List.of("ServiceA")))),
            MockPolicy.CURRENT);

    assertThat(causes).containsExactly("ServiceA");
  }

  /** FILTER_MOCKED_PATHS must drop a cause when every witness path contains a mocked type. */
  @Test
  void filterMockedPathsExcludesCauseWhenAllPathsBlocked() {
    final Set<String> causes =
        evaluator.evaluateCauses(
            Set.of("Downstream"),
            Set.of("Mid"),
            Set.of("Root"),
            Map.of("Downstream", Set.of("Root")),
            Map.of("Downstream", Map.of("Root", Set.of(List.of("Mid", "Root")))),
            MockPolicy.FILTER_MOCKED_PATHS);

    assertThat(causes).isEmpty();
  }

  /** FILTER_MOCKED_PATHS must keep a cause when at least one witness path is unblocked. */
  @Test
  void filterMockedPathsKeepsCauseWhenOnePathUnblocked() {
    final Set<String> causes =
        evaluator.evaluateCauses(
            Set.of("Downstream"),
            Set.of("Mid"),
            Set.of("Root"),
            Map.of("Downstream", Set.of("Root")),
            Map.of(
                "Downstream",
                Map.of("Root", Set.of(List.of("Mid", "Root"), List.of("Other", "Root")))),
            MockPolicy.FILTER_MOCKED_PATHS);

    assertThat(causes).containsExactly("Root");
  }

  /**
   * FILTER_MOCKED_PATHS must exclude a cause when no witness paths are recorded for any referenced
   * type — a cause without proof of dependency is not retained.
   */
  @Test
  void filterMockedPathsExcludesCauseWithoutWitnessPaths() {
    final Set<String> causes =
        evaluator.evaluateCauses(
            Set.of("Downstream"),
            Set.of(),
            Set.of("Root"),
            Map.of("Downstream", Set.of("Root")),
            Map.of(),
            MockPolicy.FILTER_MOCKED_PATHS);

    assertThat(causes).isEmpty();
  }

  /** Causes from multiple referenced types must be aggregated into a single result set. */
  @Test
  void multipleReferencedTypesAggregateIntoCauseSet() {
    final Set<String> causes =
        evaluator.evaluateCauses(
            Set.of("ServiceA", "ServiceB"),
            Set.of(),
            Set.of("RootA", "RootB"),
            Map.of("ServiceA", Set.of("RootA"), "ServiceB", Set.of("RootB")),
            Map.of(
                "ServiceA", Map.of("RootA", Set.of(List.of("ServiceA"))),
                "ServiceB", Map.of("RootB", Set.of(List.of("ServiceB")))),
            MockPolicy.CURRENT);

    assertThat(causes).containsExactlyInAnyOrder("RootA", "RootB");
  }
}
