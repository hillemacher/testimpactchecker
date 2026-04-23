package io.github.hillemacher.testimpactchecker;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the resolution rules for the configurable cache directory introduced to support Gradle
 * integration (build/test-impact/cache/) without breaking existing CLI users.
 */
class TestImpactCheckerCacheDirectoryTest {

  @TempDir Path tempDir;

  @Test
  void defaultsToProjectRelativeCacheDirectoryWhenConfigMissing() {
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    final Path resolved = TestImpactChecker.resolveCacheDirectory(tempDir, config);
    assertThat(resolved).isEqualTo(tempDir.resolve(TestImpactChecker.DEFAULT_CACHE_DIRECTORY_NAME));
  }

  @Test
  void relativeCacheDirectoryResolvesFromProjectRoot() {
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setCacheDirectoryPath("build/test-impact/cache");
    final Path resolved = TestImpactChecker.resolveCacheDirectory(tempDir, config);
    assertThat(resolved).isEqualTo(tempDir.resolve("build/test-impact/cache"));
  }

  @Test
  void absoluteCacheDirectoryIsHonoredAsIs() {
    final Path absolute = tempDir.resolve("absolute-cache");
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setCacheDirectoryPath(absolute.toString());
    final Path resolved = TestImpactChecker.resolveCacheDirectory(tempDir, config);
    assertThat(resolved).isEqualTo(absolute);
  }

  @Test
  void blankCacheDirectoryFallsBackToDefault() {
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setCacheDirectoryPath("   ");
    final Path resolved = TestImpactChecker.resolveCacheDirectory(tempDir, config);
    assertThat(resolved).isEqualTo(tempDir.resolve(TestImpactChecker.DEFAULT_CACHE_DIRECTORY_NAME));
  }

  @Test
  void clearCacheRemovesTheConfiguredDirectory() throws IOException {
    final Path customCacheDir = tempDir.resolve("build/my-cache");
    Files.createDirectories(customCacheDir);
    Files.writeString(customCacheDir.resolve("type-index.v1.json"), "{}");
    assertThat(customCacheDir.resolve("type-index.v1.json")).exists();

    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setCacheDirectoryPath("build/my-cache");

    TestImpactChecker.clearCache(tempDir, config);

    assertThat(customCacheDir).doesNotExist();
    assertThat(tempDir.resolve(TestImpactChecker.DEFAULT_CACHE_DIRECTORY_NAME)).doesNotExist();
  }

  @Test
  void clearCacheIsSafeWhenDirectoryDoesNotExist() throws IOException {
    final ImpactCheckerConfig config = new ImpactCheckerConfig();
    config.setCacheDirectoryPath("build/does-not-exist");
    TestImpactChecker.clearCache(tempDir, config);
  }
}
