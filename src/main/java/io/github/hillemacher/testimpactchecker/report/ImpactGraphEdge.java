package io.github.hillemacher.testimpactchecker.report;

import lombok.NonNull;

/**
 * Directed graph edge between two impact graph nodes.
 *
 * @param fromNodeId source node id
 * @param toNodeId target node id
 */
public record ImpactGraphEdge(@NonNull String fromNodeId, @NonNull String toNodeId) {
}
