package io.github.hillemacher.testimpactchecker.report;

import gg.jte.Content;
import gg.jte.TemplateOutput;
import lombok.NonNull;

/**
 * Wraps trusted static resources or pre-rendered fragments so templates can inline them without
 * escaping.
 */
final class RawHtmlContent implements Content {

  private final String value;

  RawHtmlContent(@NonNull final String value) {
    this.value = value;
  }

  @Override
  public void writeTo(final TemplateOutput output) {
    output.writeContent(value);
  }
}
