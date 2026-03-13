package io.github.hillemacher.testimpactchecker.report;

import java.util.List;
import lombok.NonNull;

/**
 * Immutable graph model used by the static SVG impact graph renderer.
 *
 * @param nodes graph nodes in deterministic render order
 * @param edges graph edges in deterministic render order
 * @param stats graph size/truncation metadata
 */
public record ImpactGraph(
    @NonNull List<ImpactGraphNode> nodes,
    @NonNull List<ImpactGraphEdge> edges,
    @NonNull ImpactGraphStats stats) {

  /**
   * Validates graph structure fields.
   *
   * @param nodes graph nodes in deterministic render order
   * @param edges graph edges in deterministic render order
   * @param stats graph size/truncation metadata
   * @throws NullPointerException if any parameter is {@code null}
   */
  public ImpactGraph {
    nodes = List.copyOf(nodes);
    edges = List.copyOf(edges);
  }

  /**
   * Builds an empty graph instance used for report empty states.
   *
   * @return immutable empty graph with zeroed statistics
   */
  public static ImpactGraph empty() {
    return new ImpactGraph(List.of(), List.of(), new ImpactGraphStats(0, 0, 0, 0));
  }
}
