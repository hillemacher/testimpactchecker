package io.github.hillemacher.testimpactchecker.config;

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
}
