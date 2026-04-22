package io.github.hillemacher.testimpactchecker.cli.output;

import io.github.hillemacher.testimpactchecker.ImpactDetectionReportData;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Emits the default grouped human-readable table, matching the pre-formatter CLI output byte for
 * byte so existing consumers continue to parse it.
 */
public class HumanOutputFormatter implements OutputFormatter {

  private static final String SEPARATOR = "----------------- ----------------- -----------------";
  private static final String LINE_SEPARATOR = System.lineSeparator();

  @Override
  public void write(final Path projectPath, final ImpactDetectionReportData data, final Writer out)
      throws IOException {
    final Map<Path, Set<String>> relevantTestsWithCauses = data.relevantTestsWithCauses();

    out.write(LINE_SEPARATOR);
    out.write(SEPARATOR);
    out.write(LINE_SEPARATOR);
    out.write("Relevant tests and impact causes:");
    out.write(LINE_SEPARATOR);
    if (relevantTestsWithCauses.isEmpty()) {
      out.write("None found.");
      out.write(LINE_SEPARATOR);
    } else {
      final Path normalizedProjectPath = projectPath.toAbsolutePath().normalize();
      relevantTestsWithCauses.entrySet().stream()
          .sorted(
              Comparator.comparing(
                  entry -> toRelativePath(normalizedProjectPath, entry.getKey()).toString()))
          .forEach(
              entry -> {
                final Path relativeTestPath = toRelativePath(normalizedProjectPath, entry.getKey());
                final String causes =
                    entry.getValue().stream().sorted().collect(Collectors.joining(", "));
                try {
                  out.write(relativeTestPath.toString());
                  out.write(LINE_SEPARATOR);
                  out.write("  caused by: ");
                  out.write(causes);
                  out.write(LINE_SEPARATOR);
                } catch (final IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
    out.flush();
  }

  private static Path toRelativePath(final Path normalizedProjectPath, final Path path) {
    return normalizedProjectPath.relativize(path.toAbsolutePath().normalize());
  }
}
