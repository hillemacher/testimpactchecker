package io.github.hillemacher.testimpactchecker.java.analysis;

import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.git.GitImpactUtils;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffEntry;

@RequiredArgsConstructor
@Slf4j
public class ChangedClassLocator {

  private final ImpactCheckerConfig impactCheckerConfig;

  /**
   * Resolves changed Java source files that belong to production source roots.
   *
   * <p>The locator combines committed and uncommitted git changes from
   * {@link io.github.hillemacher.testimpactchecker.git.GitImpactUtils} and keeps only
   * Java files that live under discovered {@code src/main/java} directories.
   * Results are returned as repository-relative paths to keep downstream matching stable
   * across absolute working-directory differences.
   *
   * @param mainJavaDirs directories considered main Java sources
   * @param gitRepoPath repository root path
   * @return changed Java class file paths relative to repository root
   */
  public Set<Path> findChangedClassPaths(final Set<Path> mainJavaDirs, final Path gitRepoPath) {
    final Set<Path> changedClassPath = new HashSet<>();
    final List<DiffEntry> diffs = GitImpactUtils.getDiffEntries(gitRepoPath, impactCheckerConfig);

    log.debug(
        "Found diffs {}",
        diffs.stream().map(DiffEntry::toString).toList());

    for (final DiffEntry diff : diffs) {
      final String currentDiffPath = diff.getNewPath();
      if (!currentDiffPath.endsWith(".java")) {
        continue;
      }

      for (final Path mainDir : mainJavaDirs) {
        final Path mainDirAbs = mainDir.toAbsolutePath().normalize();
        final Path fileAbs = gitRepoPath.resolve(currentDiffPath).toAbsolutePath().normalize();
        if (fileAbs.startsWith(mainDirAbs)) {
          changedClassPath.add(gitRepoPath.toAbsolutePath().normalize().relativize(fileAbs));
        }
      }
    }

    return changedClassPath;
  }
}
