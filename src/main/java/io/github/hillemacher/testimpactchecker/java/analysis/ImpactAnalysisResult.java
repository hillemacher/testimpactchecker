package io.github.hillemacher.testimpactchecker.java.analysis;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Full impact analysis output including test-level and type-level propagation details.
 *
 * @param relevantTestsWithCauses impacted tests mapped to root changed-class causes
 * @param propagationResult propagated impacted types and witness paths used by evaluation
 */
public record ImpactAnalysisResult(
    Map<Path, Set<String>> relevantTestsWithCauses,
    ImpactPropagationResult propagationResult) {
}
