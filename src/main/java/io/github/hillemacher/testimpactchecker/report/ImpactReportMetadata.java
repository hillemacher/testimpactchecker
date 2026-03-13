package io.github.hillemacher.testimpactchecker.report;

import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/**
 * Immutable execution metadata rendered alongside the static HTML report.
 *
 * @param projectPath normalized absolute project path used for analysis
 * @param generatedAtUtc UTC instant when the report model was created
 * @param executionZoneId system timezone of the machine that generated the report
 * @param baseRef effective base Git reference used for the diff, when available
 * @param targetRef effective target Git reference used for the diff, when available
 * @param annotations effective annotation names used to select relevant tests
 * @param analysisMode effective analysis mode used for impact detection
 * @param maxPropagationDepth effective transitive propagation depth
 * @param mockPolicy effective mock-handling policy used during analysis
 * @param configPath normalized absolute configuration file path, when a config file was used
 */
public record ImpactReportMetadata(
    @NonNull Path projectPath,
    @NonNull Instant generatedAtUtc,
    @NonNull ZoneId executionZoneId,
    @NonNull Optional<String> baseRef,
    @NonNull Optional<String> targetRef,
    @NonNull List<String> annotations,
    @NonNull AnalysisMode analysisMode,
    int maxPropagationDepth,
    @NonNull MockPolicy mockPolicy,
    @NonNull Optional<Path> configPath) {

  /**
   * Validates and normalizes report metadata.
   *
   * @param projectPath normalized absolute project path used for analysis
   * @param generatedAtUtc UTC instant when the report model was created
   * @param executionZoneId system timezone of the machine that generated the report
   * @param baseRef effective base Git reference used for the diff, when available
   * @param targetRef effective target Git reference used for the diff, when available
   * @param annotations effective annotation names used to select relevant tests
   * @param analysisMode effective analysis mode used for impact detection
   * @param maxPropagationDepth effective transitive propagation depth
   * @param mockPolicy effective mock-handling policy used during analysis
   * @param configPath normalized absolute configuration file path, when a config file was used
   * @throws NullPointerException if any required reference parameter is {@code null}
   * @throws IllegalArgumentException if {@code maxPropagationDepth} is negative
   */
  public ImpactReportMetadata {
    if (maxPropagationDepth < 0) {
      throw new IllegalArgumentException("maxPropagationDepth must be >= 0");
    }

    projectPath = projectPath.toAbsolutePath().normalize();
    annotations = List.copyOf(annotations);
    configPath = configPath.map(path -> path.toAbsolutePath().normalize());
  }
}
