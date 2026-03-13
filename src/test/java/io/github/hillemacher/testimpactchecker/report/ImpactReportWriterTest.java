package io.github.hillemacher.testimpactchecker.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests report output-path resolution and filesystem write behavior. */
class ImpactReportWriterTest {

  @TempDir Path tempDir;

  /** Verifies paths ending with .html are treated as explicit file targets. */
  @Test
  void testResolveOutputPathTreatsHtmlSuffixAsFilePath() {
    final ImpactReportWriter writer = new ImpactReportWriter();

    final Path resolved = writer.resolveOutputPath(tempDir, "reports/custom-report.html");

    assertThat(resolved).isEqualTo(tempDir.resolve("reports/custom-report.html"));
  }

  /** Verifies directory-like paths append the default report file name. */
  @Test
  void testResolveOutputPathTreatsDirectoryAsDefaultFileName() {
    final ImpactReportWriter writer = new ImpactReportWriter();

    final Path resolved = writer.resolveOutputPath(tempDir, "reports");

    assertThat(resolved).isEqualTo(tempDir.resolve("reports/impact-report.html"));
  }

  /** Verifies missing parent directories are created before writing report content. */
  @Test
  void testWriteReportCreatesMissingDirectories() throws IOException {
    final ImpactReportWriter writer = new ImpactReportWriter();
    final Path outputPath = tempDir.resolve("nested/report.html");

    writer.writeReport(outputPath, "<html>ok</html>");

    assertThat(outputPath).exists();
    assertThat(Files.readString(outputPath, StandardCharsets.UTF_8)).isEqualTo("<html>ok</html>");
  }

  /** Verifies write failures propagate when the parent path cannot be used as a directory. */
  @Test
  void testWriteReportFailsWhenParentIsAFile() throws IOException {
    final ImpactReportWriter writer = new ImpactReportWriter();
    final Path fileParent = tempDir.resolve("not-a-directory");
    Files.writeString(fileParent, "x", StandardCharsets.UTF_8);
    final Path outputPath = fileParent.resolve("report.html");

    assertThatThrownBy(() -> writer.writeReport(outputPath, "content"))
        .isInstanceOf(IOException.class);
  }
}
