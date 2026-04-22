package io.github.hillemacher.testimpactchecker.cli.output;

import io.github.hillemacher.testimpactchecker.ImpactDetectionReportData;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;

/** Serializes impact analysis results to a given writer in a specific textual format. */
public interface OutputFormatter {

  /**
   * Writes the impact analysis results to the given writer. Implementations must not close the
   * writer and must flush at the end so callers can attach further output.
   *
   * @param projectPath repository root used to relativize test paths where applicable
   * @param data impact analysis results
   * @param out destination writer
   * @throws IOException when the underlying writer fails
   */
  void write(Path projectPath, ImpactDetectionReportData data, Writer out) throws IOException;
}
