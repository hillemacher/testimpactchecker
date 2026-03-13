package io.github.hillemacher.testimpactchecker.report;

/**
 * Size and truncation metadata for the rendered impact graph.
 *
 * @param totalNodes total nodes available before capping
 * @param shownNodes nodes included after capping
 * @param totalEdges total edges available before capping
 * @param shownEdges edges included after capping
 */
public record ImpactGraphStats(int totalNodes, int shownNodes, int totalEdges, int shownEdges) {

  /**
   * Validates graph statistics values.
   *
   * @param totalNodes total nodes available before capping
   * @param shownNodes nodes included after capping
   * @param totalEdges total edges available before capping
   * @param shownEdges edges included after capping
   * @throws IllegalArgumentException if values are negative or shown values exceed totals
   */
  public ImpactGraphStats {
    if (totalNodes < 0 || shownNodes < 0 || totalEdges < 0 || shownEdges < 0) {
      throw new IllegalArgumentException("graph stats must be >= 0");
    }
    if (shownNodes > totalNodes) {
      throw new IllegalArgumentException("shownNodes must be <= totalNodes");
    }
    if (shownEdges > totalEdges) {
      throw new IllegalArgumentException("shownEdges must be <= totalEdges");
    }
  }

  /**
   * Returns whether the displayed graph is truncated compared to full data.
   *
   * @return {@code true} when shown nodes or edges are less than totals
   */
  public boolean isTruncated() {
    return shownNodes < totalNodes || shownEdges < totalEdges;
  }
}
