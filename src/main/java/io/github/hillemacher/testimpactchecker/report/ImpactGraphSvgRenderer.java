package io.github.hillemacher.testimpactchecker.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.NonNull;

/** Renders {@link ImpactGraph} as deterministic inline SVG with layered lanes. */
public class ImpactGraphSvgRenderer {

  private static final int WIDTH = 1120;
  private static final int LANE_HEADER_Y = 28;
  private static final int NODE_WIDTH = 290;
  private static final int NODE_HEIGHT = 24;
  private static final int NODE_SPACING = 10;
  private static final int LANE_START_Y = 48;
  private static final int LANE_CAUSE_X = 40;
  private static final int LANE_TYPE_X = 415;
  private static final int LANE_TEST_X = 790;

  /**
   * Renders a complete SVG fragment for the given graph.
   *
   * @param graph graph model to render
   * @return inline SVG markup, or an empty-state paragraph when no graph nodes are present
   * @throws NullPointerException if {@code graph} is {@code null}
   */
  public String render(@NonNull final ImpactGraph graph) {
    if (graph.nodes().isEmpty()) {
      return "<p class=\"empty-state\">No impact graph data available.</p>";
    }

    final List<ImpactGraphNode> causeNodes = nodesByKind(graph, ImpactGraphNodeKind.CAUSE);
    final List<ImpactGraphNode> typeNodes = nodesByKind(graph, ImpactGraphNodeKind.TYPE);
    final List<ImpactGraphNode> testNodes = nodesByKind(graph, ImpactGraphNodeKind.TEST);

    final int laneHeight =
        maxLaneNodeCount(causeNodes, typeNodes, testNodes) * (NODE_HEIGHT + NODE_SPACING);
    final int height = Math.max(260, LANE_START_Y + laneHeight + 28);

    final Map<String, NodeBox> boxesByNodeId = buildNodeBoxes(causeNodes, typeNodes, testNodes);

    final StringBuilder svg = new StringBuilder();
    svg.append("<svg class=\"impact-graph-svg\" viewBox=\"0 0 ")
        .append(WIDTH)
        .append(' ')
        .append(height)
        .append("\" role=\"img\" aria-label=\"Impact graph\">\n");
    svg.append("  <rect x=\"0\" y=\"0\" width=\"")
        .append(WIDTH)
        .append("\" height=\"")
        .append(height)
        .append("\" fill=\"#f9fbfc\"/>\n");

    renderLaneHeaders(svg);
    renderEdges(graph, boxesByNodeId, svg);
    renderNodes(causeNodes, boxesByNodeId, svg);
    renderNodes(typeNodes, boxesByNodeId, svg);
    renderNodes(testNodes, boxesByNodeId, svg);

    svg.append("</svg>\n");
    return svg.toString();
  }

  private List<ImpactGraphNode> nodesByKind(
      final ImpactGraph graph, final ImpactGraphNodeKind kind) {
    return graph.nodes().stream().filter(node -> node.kind() == kind).toList();
  }

  private int maxLaneNodeCount(
      final List<ImpactGraphNode> causeNodes,
      final List<ImpactGraphNode> typeNodes,
      final List<ImpactGraphNode> testNodes) {
    return Math.max(causeNodes.size(), Math.max(typeNodes.size(), testNodes.size()));
  }

  private Map<String, NodeBox> buildNodeBoxes(
      final List<ImpactGraphNode> causeNodes,
      final List<ImpactGraphNode> typeNodes,
      final List<ImpactGraphNode> testNodes) {
    final Map<String, NodeBox> boxesByNodeId = new HashMap<>();
    placeNodes(causeNodes, LANE_CAUSE_X, boxesByNodeId);
    placeNodes(typeNodes, LANE_TYPE_X, boxesByNodeId);
    placeNodes(testNodes, LANE_TEST_X, boxesByNodeId);
    return boxesByNodeId;
  }

  private void placeNodes(
      final List<ImpactGraphNode> nodes,
      final int laneX,
      final Map<String, NodeBox> boxesByNodeId) {
    for (int i = 0; i < nodes.size(); i++) {
      final ImpactGraphNode node = nodes.get(i);
      final int y = LANE_START_Y + i * (NODE_HEIGHT + NODE_SPACING);
      boxesByNodeId.put(node.id(), new NodeBox(laneX, y));
    }
  }

  private void renderLaneHeaders(final StringBuilder svg) {
    svg.append(textNode(LANE_CAUSE_X, LANE_HEADER_Y, "Changed causes", "lane-header"));
    svg.append(textNode(LANE_TYPE_X, LANE_HEADER_Y, "Impacted types", "lane-header"));
    svg.append(textNode(LANE_TEST_X, LANE_HEADER_Y, "Impacted tests", "lane-header"));
  }

  private void renderEdges(
      final ImpactGraph graph, final Map<String, NodeBox> boxesByNodeId, final StringBuilder svg) {
    final List<ImpactGraphEdge> sortedEdges = new ArrayList<>(graph.edges());
    sortedEdges.sort(
        Comparator.comparing(ImpactGraphEdge::fromNodeId).thenComparing(ImpactGraphEdge::toNodeId));

    for (final ImpactGraphEdge edge : sortedEdges) {
      final NodeBox fromBox = boxesByNodeId.get(edge.fromNodeId());
      final NodeBox toBox = boxesByNodeId.get(edge.toNodeId());
      if (fromBox == null || toBox == null) {
        continue;
      }

      svg.append("  <line class=\"graph-edge\" x1=\"")
          .append(fromBox.x() + NODE_WIDTH)
          .append("\" y1=\"")
          .append(fromBox.y() + NODE_HEIGHT / 2)
          .append("\" x2=\"")
          .append(toBox.x())
          .append("\" y2=\"")
          .append(toBox.y() + NODE_HEIGHT / 2)
          .append("\"/>\n");
    }
  }

  private void renderNodes(
      final List<ImpactGraphNode> nodes,
      final Map<String, NodeBox> boxesByNodeId,
      final StringBuilder svg) {
    for (final ImpactGraphNode node : nodes) {
      final NodeBox box = boxesByNodeId.get(node.id());
      if (box == null) {
        continue;
      }

      svg.append("  <rect class=\"graph-node ")
          .append(cssClassForKind(node.kind()))
          .append("\" x=\"")
          .append(box.x())
          .append("\" y=\"")
          .append(box.y())
          .append("\" width=\"")
          .append(NODE_WIDTH)
          .append("\" height=\"")
          .append(NODE_HEIGHT)
          .append("\" rx=\"6\"/>\n");

      svg.append(textNode(box.x() + 8, box.y() + 16, trimLabel(node.label()), "graph-label"));
    }
  }

  private String cssClassForKind(final ImpactGraphNodeKind kind) {
    return switch (kind) {
      case CAUSE -> "graph-node-cause";
      case TYPE -> "graph-node-type";
      case TEST -> "graph-node-test";
    };
  }

  private String trimLabel(final String label) {
    if (label.length() <= 42) {
      return label;
    }
    return label.substring(0, 39) + "...";
  }

  private String textNode(final int x, final int y, final String text, final String cssClass) {
    return String.format(
        Locale.ROOT,
        "  <text class=\"%s\" x=\"%d\" y=\"%d\">%s</text>%n",
        cssClass,
        x,
        y,
        escapeXml(text));
  }

  private String escapeXml(final String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private record NodeBox(int x, int y) {}
}
