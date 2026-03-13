package io.github.hillemacher.testimpactchecker.report;

import java.util.List;
import lombok.NonNull;

/**
 * Immutable collection of graph views used by the HTML report.
 *
 * @param fullGraph full selected/capped graph used as the source of detailed views
 * @param overviewGraph simplified overview graph optimized for readability
 * @param causeSections focused per-cause graph sections shown below the overview
 */
public record ImpactGraphBundle(
    @NonNull ImpactGraph fullGraph,
    @NonNull ImpactGraph overviewGraph,
    @NonNull List<ImpactGraphSection> causeSections) {

  /**
   * Validates graph view bundle fields.
   *
   * @param fullGraph full selected/capped graph used as the source of detailed views
   * @param overviewGraph simplified overview graph optimized for readability
   * @param causeSections focused per-cause graph sections shown below the overview
   * @throws NullPointerException if any parameter is {@code null}
   */
  public ImpactGraphBundle {
    causeSections = List.copyOf(causeSections);
  }

  /**
   * Creates an empty graph bundle for report empty states.
   *
   * @return immutable graph bundle with empty overview and no cause sections
   */
  public static ImpactGraphBundle empty() {
    final ImpactGraph emptyGraph = ImpactGraph.empty();
    return new ImpactGraphBundle(emptyGraph, emptyGraph, List.of());
  }
}
