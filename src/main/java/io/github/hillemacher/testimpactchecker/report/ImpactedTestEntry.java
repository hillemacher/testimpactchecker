package io.github.hillemacher.testimpactchecker.report;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;

/**
 * One impacted test entry in the generated impact report.
 *
 * @param relativeTestPath test path relative to the analyzed project root
 * @param causes sorted list of changed classes causing this test to be impacted
 */
public record ImpactedTestEntry(@NonNull Path relativeTestPath, @NonNull List<String> causes) {

  /**
   * Validates the entry and stores causes in deterministic sort order.
   *
   * @param relativeTestPath test path relative to the analyzed project root
   * @param causes changed classes causing this test to be impacted
   * @throws NullPointerException if any parameter is {@code null}
   */
  public ImpactedTestEntry {
    final List<String> sortedCauses = new ArrayList<>(causes);
    sortedCauses.sort(String::compareTo);
    causes = List.copyOf(sortedCauses);
  }
}
