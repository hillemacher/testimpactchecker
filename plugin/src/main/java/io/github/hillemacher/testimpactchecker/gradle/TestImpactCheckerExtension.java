package io.github.hillemacher.testimpactchecker.gradle;

import io.github.hillemacher.testimpactchecker.TestImpactChecker;
import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * DSL configuration for the {@code testImpact} extension.
 *
 * <p>Defaults are set in {@link TestImpactCheckerPlugin} via {@code convention(...)} so users only
 * need to override the fields they care about.
 */
public abstract class TestImpactCheckerExtension {

  public abstract Property<String> getBaseRef();

  public abstract Property<String> getTargetRef();

  public abstract ListProperty<String> getAnnotations();

  public abstract Property<AnalysisMode> getAnalysisMode();

  public abstract Property<MockPolicy> getMockPolicy();

  public abstract Property<Integer> getMaxPropagationDepth();

  public abstract Property<TestImpactChecker.CacheMode> getCacheMode();

  /**
   * Directory used to persist the main-source index cache. Defaults to {@code
   * build/test-impact/cache/} so it participates in Gradle's own caching.
   */
  public abstract DirectoryProperty getCacheDirectory();

  /**
   * Destination for the JSON impact artifact. Defaults to {@code build/test-impact/impact.json}.
   */
  public abstract RegularFileProperty getImpactJsonOutput();

  /**
   * Destination for the HTML impact report. Defaults to {@code
   * build/test-impact/impact-report.html}.
   */
  public abstract RegularFileProperty getImpactHtmlOutput();

  /** Project root analyzed by the checker. Defaults to the project directory. */
  public abstract DirectoryProperty getProjectDirectory();

  /** Populated by the plugin at apply-time; used by {@link #applyTo(TaskProvider)}. */
  private TaskProvider<TestImpactCheckTask> checkTaskProvider;

  void wireCheckTask(final TaskProvider<TestImpactCheckTask> provider) {
    this.checkTaskProvider = provider;
  }

  /**
   * Wires the impacted FQCN set into the given {@code Test} task's filter. The test task gains a
   * dependency on {@code testImpactCheck} and, at execution time, only runs classes the checker
   * reports as impacted. When the impacted set is empty, the filter is configured to match nothing
   * and {@code failOnNoMatchingTests} is disabled, so the test task reports success without
   * executing anything.
   */
  public void applyTo(final TaskProvider<Test> testTaskProvider) {
    if (checkTaskProvider == null) {
      throw new IllegalStateException(
          "applyTo(test) may only be called after the testImpactChecker plugin is applied");
    }
    final TaskProvider<TestImpactCheckTask> check = checkTaskProvider;
    testTaskProvider.configure(
        testTask -> {
          testTask.dependsOn(check);
          testTask.doFirst(
              new ApplyImpactedFilterAction(
                  check.flatMap(TestImpactCheckTask::getImpactJsonOutput)));
        });
  }
}
