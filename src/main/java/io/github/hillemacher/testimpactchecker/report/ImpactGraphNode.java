package io.github.hillemacher.testimpactchecker.report;

import lombok.NonNull;

/**
 * One graph node in the report impact graph.
 *
 * @param id deterministic internal identifier
 * @param label display label shown in the graph
 * @param kind node lane category
 * @param score ranking score used for deterministic ordering and capping
 */
public record ImpactGraphNode(
    @NonNull String id,
    @NonNull String label,
    @NonNull ImpactGraphNodeKind kind,
    int score) {

  /**
   * Validates graph node fields.
   */
  public ImpactGraphNode {
    if (score < 0) {
      throw new IllegalArgumentException("score must be >= 0");
    }
  }
}
