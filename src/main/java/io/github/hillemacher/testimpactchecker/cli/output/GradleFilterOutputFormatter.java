package io.github.hillemacher.testimpactchecker.cli.output;

import io.github.hillemacher.testimpactchecker.ImpactDetectionReportData;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;

/**
 * Emits one fully-qualified test class name per line, suitable for Gradle's {@code test --tests}
 * argument form. Tests for which an FQCN could not be determined are skipped.
 */
public class GradleFilterOutputFormatter implements OutputFormatter {

  @Override
  public void write(final Path projectPath, final ImpactDetectionReportData data, final Writer out)
      throws IOException {
    final Map<Path, String> fqcnByTest = data.testFileToFqcn();
    if (fqcnByTest == null) {
      out.flush();
      return;
    }
    final TreeSet<String> sortedFqcns = new TreeSet<>(fqcnByTest.values());
    for (final String fqcn : sortedFqcns) {
      out.write(fqcn);
      out.write(System.lineSeparator());
    }
    out.flush();
  }
}
