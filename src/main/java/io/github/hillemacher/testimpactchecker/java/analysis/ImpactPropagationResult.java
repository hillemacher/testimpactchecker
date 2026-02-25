package io.github.hillemacher.testimpactchecker.java.analysis;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds propagated impact results for downstream test evaluation.
 *
 * @param impactedTypeToCauses impacted type name to root changed-class causes
 * @param witnessPathsByTypeAndCause impacted type/cause to witness paths used by mock filtering
 */
public record ImpactPropagationResult(
    Map<String, Set<String>> impactedTypeToCauses,
    Map<String, Map<String, Set<List<String>>>> witnessPathsByTypeAndCause) {
}
