package io.github.hillemacher.testimpactchecker.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hillemacher.testimpactchecker.config.AnalysisMode;
import io.github.hillemacher.testimpactchecker.config.MockPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Tests for loading impact checker configuration from JSON.
 */
class ImpactCheckerConfigTest {

  /**
   * Validates JSON configuration values are deserialized as expected.
   */
  @Test
  void testReadConfig() throws IOException {
    final Path configFilePath = Paths.get("src", "test", "resources", "configs", "test-config.json");

    final ObjectMapper mapper = new ObjectMapper();
    final ImpactCheckerConfig impactCheckerConfig = mapper.readValue(configFilePath.toFile(),
        ImpactCheckerConfig.class);

    assertThat(impactCheckerConfig).isNotNull();
    assertThat(impactCheckerConfig.getAnnotations())
        .containsOnly("ContextConfiguration", "MyCustomAnnotation");
    assertThat(impactCheckerConfig.getBaseRef()).isEqualTo("develop");
    assertThat(impactCheckerConfig.getTargetRef()).isEqualTo("HEAD");
    assertThat(impactCheckerConfig.getAnalysisMode()).isEqualTo(AnalysisMode.DIRECT);
    assertThat(impactCheckerConfig.getMaxPropagationDepth()).isEqualTo(2);
    assertThat(impactCheckerConfig.getMockPolicy()).isEqualTo(MockPolicy.CURRENT);
  }
}
