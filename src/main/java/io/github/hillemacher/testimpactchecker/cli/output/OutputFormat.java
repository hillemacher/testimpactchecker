package io.github.hillemacher.testimpactchecker.cli.output;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Supported output formats for the CLI's {@code --format} option. */
public enum OutputFormat {
  HUMAN("human"),
  JSON("json"),
  GRADLE_FILTER("gradle-filter"),
  JUNIT_INCLUDES("junit-includes");

  private final String cliName;

  OutputFormat(final String cliName) {
    this.cliName = cliName;
  }

  public String cliName() {
    return cliName;
  }

  /**
   * Parses the CLI token for this format. Matching is case-insensitive.
   *
   * @throws IllegalArgumentException when the token does not match a known format
   */
  public static OutputFormat fromCli(final String value) {
    final String normalized = value.toLowerCase(Locale.ROOT);
    for (final OutputFormat format : values()) {
      if (format.cliName.equals(normalized)) {
        return format;
      }
    }
    throw new IllegalArgumentException(
        "Unknown --format value '"
            + value
            + "'. Supported values: "
            + Arrays.stream(values()).map(OutputFormat::cliName).collect(Collectors.joining(", ")));
  }
}
