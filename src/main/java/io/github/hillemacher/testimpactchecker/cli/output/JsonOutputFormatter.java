package io.github.hillemacher.testimpactchecker.cli.output;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.hillemacher.testimpactchecker.ImpactDetectionReportData;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Emits impact analysis results as a versioned JSON document.
 *
 * <p>Schema version 1:
 *
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "impactedTests": [
 *     { "file": "path/relative/to/project", "fqcn": "com.example.FooTest", "causes": ["A", "B"] }
 *   ],
 *   "stats": { "impactedTestCount": N, "uniqueCauseCount": N }
 * }
 * </pre>
 */
public class JsonOutputFormatter implements OutputFormatter {

  public static final int SCHEMA_VERSION = 1;

  private final ObjectMapper objectMapper;

  public JsonOutputFormatter() {
    this.objectMapper =
        new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.CLOSE_CLOSEABLE);
    this.objectMapper.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
  }

  @Override
  public void write(final Path projectPath, final ImpactDetectionReportData data, final Writer out)
      throws IOException {
    final Path normalizedProjectPath = projectPath.toAbsolutePath().normalize();
    final Map<Path, Set<String>> relevantTestsWithCauses = data.relevantTestsWithCauses();
    final Map<Path, String> fqcnByTest = data.testFileToFqcn();

    final List<ImpactedTestDto> impactedTests = new ArrayList<>();
    final Set<String> uniqueCauses = new HashSet<>();

    relevantTestsWithCauses.entrySet().stream()
        .sorted(
            Comparator.comparing(
                entry ->
                    normalizedProjectPath
                        .relativize(entry.getKey().toAbsolutePath().normalize())
                        .toString()))
        .forEach(
            entry -> {
              final Path relativeTestPath =
                  normalizedProjectPath.relativize(entry.getKey().toAbsolutePath().normalize());
              final Set<String> sortedCauses = new TreeSet<>(entry.getValue());
              uniqueCauses.addAll(entry.getValue());
              impactedTests.add(
                  new ImpactedTestDto(
                      relativeTestPath.toString().replace('\\', '/'),
                      fqcnByTest == null ? null : fqcnByTest.get(entry.getKey()),
                      new ArrayList<>(sortedCauses)));
            });

    final ReportDto dto =
        new ReportDto(
            SCHEMA_VERSION, impactedTests, new StatsDto(impactedTests.size(), uniqueCauses.size()));

    objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, dto);
    out.write(System.lineSeparator());
    out.flush();
  }

  @JsonPropertyOrder({"schemaVersion", "impactedTests", "stats"})
  private record ReportDto(
      int schemaVersion, List<ImpactedTestDto> impactedTests, StatsDto stats) {}

  @JsonPropertyOrder({"file", "fqcn", "causes"})
  private record ImpactedTestDto(String file, String fqcn, List<String> causes) {}

  @JsonPropertyOrder({"impactedTestCount", "uniqueCauseCount"})
  private record StatsDto(int impactedTestCount, int uniqueCauseCount) {}
}
