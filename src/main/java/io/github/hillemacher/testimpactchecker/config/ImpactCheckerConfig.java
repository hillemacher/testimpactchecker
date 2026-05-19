package io.github.hillemacher.testimpactchecker.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for defining annotations, Git refs, and optional report output settings used by
 * impact detection.
 */
@Getter
@Setter
public class ImpactCheckerConfig {

  private List<String> annotations;

  private String baseRef;

  private String targetRef;

  private AnalysisMode analysisMode = AnalysisMode.DIRECT;

  private Integer maxPropagationDepth = 2;

  private MockPolicy mockPolicy = MockPolicy.CURRENT;

  /**
   * Returns configured analysis mode with a safe default.
   *
   * <p>This getter is null-safe so partially specified JSON configuration files keep
   * backward-compatible behavior.
   *
   * @return configured analysis mode, or {@link AnalysisMode#DIRECT} when unset
   */
  public AnalysisMode getAnalysisMode() {
    return analysisMode == null ? AnalysisMode.DIRECT : analysisMode;
  }

  /**
   * Returns non-negative propagation depth with a safe default.
   *
   * <p>Negative values are clamped to {@code 0} to avoid invalid traversal settings.
   *
   * @return configured non-negative propagation depth, defaulting to {@code 2}
   */
  public int getMaxPropagationDepth() {
    return maxPropagationDepth == null ? 2 : Math.max(0, maxPropagationDepth);
  }

  /**
   * Returns configured mock policy with a safe default.
   *
   * <p>This getter is null-safe so legacy configurations continue to behave as {@link
   * MockPolicy#CURRENT}.
   *
   * @return configured mock policy, or {@link MockPolicy#CURRENT} when unset
   */
  public MockPolicy getMockPolicy() {
    return mockPolicy == null ? MockPolicy.CURRENT : mockPolicy;
  }

  /**
   * Optional path or directory for static HTML report output.
   *
   * <p>When set and {@code --html-report} is not passed on the command line, this value is used as
   * the report target.
   */
  private String htmlReportOutputPath;

  /**
   * Optional directory used to persist the main-source index cache.
   *
   * <p>When unset, analysis falls back to {@code <project>/.testimpactchecker/cache/}. Relative
   * paths are resolved from the analyzed project root, so Gradle integrations can point the cache
   * at {@code build/test-impact/cache/} without leaking the plugin's working directory.
   */
  private String cacheDirectoryPath;

  /**
   * Validates that the required configuration fields are present.
   *
   * <p>Jackson silently leaves missing JSON fields as {@code null}, so this method is invoked after
   * deserialization to fail fast with a descriptive error rather than propagating a {@link
   * NullPointerException} from downstream consumers.
   *
   * @throws IllegalArgumentException if {@code baseRef}, {@code targetRef}, or {@code annotations}
   *     is missing or empty
   */
  public void validate() {
    final List<String> missing = new ArrayList<>();
    if (baseRef == null || baseRef.isBlank()) {
      missing.add("baseRef");
    }
    if (targetRef == null || targetRef.isBlank()) {
      missing.add("targetRef");
    }
    if (annotations == null || annotations.isEmpty()) {
      missing.add("annotations");
    }
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          "Config is missing required field(s): " + String.join(", ", missing));
    }
  }
}
