package io.github.hillemacher.testimpactchecker.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for defining annotations and Git refs used by impact detection.
 */
@Getter
@Setter
public class ImpactCheckerConfig {

  private List<String> annotations;

  private String baseRef;

  private String targetRef;
}
