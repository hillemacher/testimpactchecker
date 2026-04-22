package io.github.hillemacher.testimpactchecker;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Report-oriented impact data needed to render summaries and topology visualizations.
 *
 * @param relevantTestsWithCauses impacted tests mapped to root changed-class causes
 * @param impactedTypeToCauses impacted production types mapped to root changed-class causes
 * @param testFileToFqcn impacted test file path to fully-qualified class name; entries may be
 *     missing when an FQCN could not be determined from the test source
 */
public record ImpactDetectionReportData(
    Map<Path, Set<String>> relevantTestsWithCauses,
    Map<String, Set<String>> impactedTypeToCauses,
    Map<Path, String> testFileToFqcn) {}
