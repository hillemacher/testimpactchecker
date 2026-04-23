package io.github.hillemacher.testimpactchecker.gradle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.Test;

/**
 * Reads the JSON impact artifact at execution time and applies the impacted FQCN set to the {@code
 * Test} task's filter. Captures only a {@link Provider} reference to stay configuration-cache
 * compatible.
 */
public class ApplyImpactedFilterAction implements Action<Task> {

  private static final String SENTINEL_PATTERN_NO_IMPACTED_TESTS =
      "__testimpactchecker_no_tests_impacted__";

  private final Provider<RegularFile> impactJsonFile;

  public ApplyImpactedFilterAction(final Provider<RegularFile> impactJsonFile) {
    this.impactJsonFile = impactJsonFile;
  }

  @Override
  public void execute(final Task task) {
    final Test test = (Test) task;
    final File jsonFile = impactJsonFile.get().getAsFile();
    final Set<String> fqcns = readImpactedFqcns(jsonFile);
    if (fqcns.isEmpty()) {
      test.getFilter().includeTestsMatching(SENTINEL_PATTERN_NO_IMPACTED_TESTS);
      test.getFilter().setFailOnNoMatchingTests(false);
      task.getLogger()
          .lifecycle(
              "testImpact: no impacted tests detected — skipping all tests in {}", task.getPath());
      return;
    }
    fqcns.forEach(test.getFilter()::includeTestsMatching);
    test.getFilter().setFailOnNoMatchingTests(false);
    task.getLogger()
        .lifecycle(
            "testImpact: running {} impacted test class(es) in {}", fqcns.size(), task.getPath());
  }

  private static Set<String> readImpactedFqcns(final File jsonFile) {
    if (!jsonFile.isFile()) {
      throw new IllegalStateException(
          "Expected impact JSON at " + jsonFile + " — did testImpactCheck run?");
    }
    try {
      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode root = mapper.readTree(jsonFile);
      final JsonNode tests = root.path("impactedTests");
      final Set<String> fqcns = new LinkedHashSet<>();
      if (tests.isArray()) {
        for (final JsonNode entry : tests) {
          final String fqcn = entry.path("fqcn").asText(null);
          if (fqcn != null && !fqcn.isBlank()) {
            fqcns.add(fqcn);
          }
        }
      }
      return fqcns;
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to read impact JSON at " + jsonFile, e);
    }
  }
}
