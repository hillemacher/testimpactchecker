package io.github.hillemacher.testimpactchecker.cli.output;

/** Factory for obtaining the {@link OutputFormatter} implementation for a given format. */
public final class OutputFormatters {

  private OutputFormatters() {}

  public static OutputFormatter forFormat(final OutputFormat format) {
    return switch (format) {
      case HUMAN -> new HumanOutputFormatter();
      case JSON -> new JsonOutputFormatter();
      case GRADLE_FILTER -> new GradleFilterOutputFormatter();
      case JUNIT_INCLUDES -> new JUnitIncludesOutputFormatter();
    };
  }
}
