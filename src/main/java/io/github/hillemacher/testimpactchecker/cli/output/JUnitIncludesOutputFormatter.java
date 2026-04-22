package io.github.hillemacher.testimpactchecker.cli.output;

import io.github.hillemacher.testimpactchecker.ImpactDetectionReportData;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;

/**
 * Emits one Surefire {@code <include>} glob per line (e.g. {@code **\/FooTest.class}) from the
 * impacted test FQCNs. Tests for which an FQCN could not be determined are skipped.
 */
public class JUnitIncludesOutputFormatter implements OutputFormatter {

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
      final int lastDot = fqcn.lastIndexOf('.');
      final String simpleName = lastDot < 0 ? fqcn : fqcn.substring(lastDot + 1);
      out.write("**/");
      out.write(simpleName);
      out.write(".class");
      out.write(System.lineSeparator());
    }
    out.flush();
  }
}
