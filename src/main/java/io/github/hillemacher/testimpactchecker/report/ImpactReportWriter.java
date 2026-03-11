package io.github.hillemacher.testimpactchecker.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.NonNull;

/**
 * Resolves report output locations and writes static report files.
 */
public class ImpactReportWriter {

  public static final String DEFAULT_REPORT_FILE_NAME = "impact-report.html";

  /**
   * Resolves a user-provided report path against the project path.
   *
   * <p>If the path ends with ".html", it is treated as a file path. Otherwise, it is treated as a
   * directory and the default report file name is appended.
   *
   * @param projectPath root directory of the analyzed project
   * @param configuredOutputPath output path value from CLI or configuration
   * @return absolute file path where the HTML report should be written
   * @throws NullPointerException if any parameter is {@code null}
   */
  public Path resolveOutputPath(
      @NonNull final Path projectPath,
      @NonNull final String configuredOutputPath) {
    final Path configuredPath = Path.of(configuredOutputPath.trim());
    final Path absolutePath = configuredPath.isAbsolute()
        ? configuredPath
        : projectPath.toAbsolutePath().normalize().resolve(configuredPath).normalize();

    // Path resolution rule: .html means direct file target, anything else means directory target.
    if (absolutePath.toString().endsWith(".html")) {
      return absolutePath;
    }
    return absolutePath.resolve(DEFAULT_REPORT_FILE_NAME);
  }

  /**
   * Writes an HTML report to disk with UTF-8 encoding, creating missing parent directories.
   *
   * @param outputPath full file path of the target HTML file
   * @param htmlContent rendered HTML content to persist
   * @throws IOException if directory creation or file writing fails
   * @throws NullPointerException if any parameter is {@code null}
   */
  public void writeReport(@NonNull final Path outputPath, @NonNull final String htmlContent)
      throws IOException {
    final Path parent = outputPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(outputPath, htmlContent, StandardCharsets.UTF_8);
  }
}
