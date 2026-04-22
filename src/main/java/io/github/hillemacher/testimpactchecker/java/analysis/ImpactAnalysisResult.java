package io.github.hillemacher.testimpactchecker.java.analysis;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Full impact analysis output including test-level and type-level propagation details.
 *
 * @param relevantTestsWithCauses impacted tests mapped to root changed-class causes
 * @param propagationResult propagated impacted types and witness paths used by evaluation
 * @param testFileToFqcn impacted test file path to fully-qualified class name; entries may be
 *     missing when an FQCN could not be determined from the test source
 */
public record ImpactAnalysisResult(
    Map<Path, Set<String>> relevantTestsWithCauses,
    ImpactPropagationResult propagationResult,
    Map<Path, String> testFileToFqcn) {}
