package io.github.hillemacher.testimpactchecker.git;

import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

/**
 * Git-related helper utilities used for detecting changed files and revisions.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GitImpactUtils {

  /**
   * Creates a tree iterator for the specified Git reference (branch, tag, or commit).
   *
   * <p>
   * This utility method is used to prepare a {@link CanonicalTreeParser} that represents the state
   * of the repository at the specified ref, so that it can be used as input for JGit diff
   * operations such as comparing two branches or commits.
   *
   * <p>
   * For example, to compare the changes between the <code>develop</code> branch and the current
   * branch (<code>HEAD</code>), use:
   *
   * <pre>
   * AbstractTreeIterator oldTree = getTreeIterator(repository, "refs/heads/develop");
   * AbstractTreeIterator newTree = getTreeIterator(repository, "HEAD");
   * List&lt;DiffEntry&gt; diffs = git.diff().setOldTree(oldTree).setNewTree(newTree).call();
   * </pre>
   *
   * @param repository the JGit {@link Repository} instance
   * @param ref the Git reference name (e.g. "refs/heads/develop", "HEAD", or a commit hash)
   * @return a {@link AbstractTreeIterator} positioned at the root of the tree for the given ref
   * @throws IOException if an error occurs while accessing the repository or resolving the ref
   */
  public static AbstractTreeIterator getTreeIterator(
      @NonNull final Repository repository,
      @NonNull final String ref)
      throws IOException {
    log.debug("Create tree iterator for {}", ref);

    ObjectId id = repository.resolve(ref);
    try (RevWalk walk = new RevWalk(repository)) {
      RevCommit commit = walk.parseCommit(id);
      RevTree tree = commit.getTree();
      CanonicalTreeParser treeParser = new CanonicalTreeParser();
      try (var reader = repository.newObjectReader()) {
        treeParser.reset(reader, tree);
      }
      walk.dispose();

      return treeParser;
    }
  }

  /**
   * Returns a combined list of distinct {@link DiffEntry} objects representing all changed files in
   * a Git repository, including both committed and uncommitted changes.
   *
   * <p>
   * This method performs two types of diff operations:
   *
   * <ul>
   * <li><b>Uncommitted changes</b>: Lists all changes in the working directory and index (staged
   * and unstaged) compared to the current HEAD.
   * <li><b>Committed changes</b>: Lists all changes between the specified {@code baseRef} and
   * {@code targetRef} in the provided configuration, typically representing the difference between
   * two branches or commits.
   * </ul>
   *
   * <p>
   * The resulting list is deduplicated by file path, so that each changed file appears only once.
   *
   * @param gitRepoPath the path to the root of the Git repository
   * @param config the configuration specifying {@code baseRef} and {@code targetRef} to compare
   * @return a list of unique {@link DiffEntry} objects representing all changed files, including
   *         both committed and uncommitted changes; returns an empty list if the repository cannot
   *         be accessed or an error occurs
   */
  public static List<DiffEntry> getDiffEntries(
      @NonNull final Path gitRepoPath,
      @NonNull final ImpactCheckerConfig config) {
    log.debug("gitRepoPath: {}", gitRepoPath);
    try (final Git git = Git.open(gitRepoPath.toFile())) {
      log.debug("Running git diff for base-ref {} and target-ref {}", config.getBaseRef(),
          config.getTargetRef());

      // 1. Uncommitted changes (working tree vs HEAD)
      List<DiffEntry> uncommitted = git.diff().setShowNameAndStatusOnly(true).call();

      // 2. Committed changes (targetRef vs baseRef)
      List<DiffEntry> committed =
          git.diff().setOldTree(getTreeIterator(git.getRepository(), config.getBaseRef()))
              .setNewTree(getTreeIterator(git.getRepository(), config.getTargetRef()))
              .setShowNameAndStatusOnly(true).call();

      // 3. Combine and deduplicate by file path (e.g., newPath)
      Set<String> seen = new HashSet<>();
      List<DiffEntry> allDiffs = new ArrayList<>();
      for (DiffEntry diff : committed) {
        String path = diff.getNewPath();
        if (seen.add(path)) {
          allDiffs.add(diff);
        }
      }
      for (DiffEntry diff : uncommitted) {
        String path = diff.getNewPath();
        if (seen.add(path)) {
          allDiffs.add(diff);
        }
      }

      return allDiffs;
    } catch (final GitAPIException | IOException e) {
      log.error("Cannot access git repo {}.", gitRepoPath, e);
      return List.of();
    }
  }
}
