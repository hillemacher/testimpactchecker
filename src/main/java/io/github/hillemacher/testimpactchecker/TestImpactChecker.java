package io.github.hillemacher.testimpactchecker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;

import io.github.hillemacher.testimpactchecker.config.ImpactCheckerConfig;
import io.github.hillemacher.testimpactchecker.git.GitImpactUtils;
import io.github.hillemacher.testimpactchecker.java.JavaImpactUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestImpactChecker {

	private static final String MAIN_JAVA_DIR_SUFFIX = "src/main/java";
	private static final String TEST_JAVA_DIR_SUFFIX = "src/test/java";

	/**
	 * Detects and returns the set of test files that are impacted by changes in the main Java source files
	 * of the specified repository.
	 * <p>
	 * This method performs the following steps:
	 * <ol>
	 *   <li>Recursively locates all {@code src/main/java} and {@code src/test/java} directories within the repository.</li>
	 *   <li>Identifies changed Java class files under all {@code src/main/java} directories using Git.</li>
	 *   <li>If no changed classes are found or the repository cannot be accessed, returns an empty set.</li>
	 *   <li>For each changed class, determines its implemented interfaces.</li>
	 *   <li>Scans all test classes for relevant annotations and references to the changed classes or their implemented interfaces,
	 *       and collects the test files that are likely to be impacted.</li>
	 * </ol>
	 * The result is a set of paths to test files that are potentially affected by the changes.
	 *
	 * @param repositoryPath the root path of the repository to scan
	 * @return a set of paths to impacted test files; returns an empty set if no changed classes are detected
	 */
	public Set<Path> detectImpact(final Path repositoryPath, final ImpactCheckerConfig impactCheckerConfig) throws IOException {
		final JavaImpactUtils javaImpactUtils = createJavaImpactUtils(impactCheckerConfig);

		// 1. Find all src/main/java directories in project (recursively)
		final Set<Path> mainJavaDirs = findAllJavaSourceDirs(repositoryPath, MAIN_JAVA_DIR_SUFFIX);
		final Set<Path> testJavaDirs = findAllJavaSourceDirs(repositoryPath, TEST_JAVA_DIR_SUFFIX);

		// 2. Get changed Java classes under all src/main/java dirs
		final Set<Path> changedClassPath = javaImpactUtils.findChangedClassPaths(mainJavaDirs, repositoryPath);

		if (changedClassPath == null) {
			return Set.of();
		}

		if (changedClassPath.isEmpty()) {
			log.info("No changed classes detected.");
			return Set.of();
		}

		log.debug("Changed classes: {}", changedClassPath.stream().map(Path::toString).collect(Collectors.joining(", ")));

		// 3. Scan all test classes for @ContextConfiguration and references to changed classes
		return javaImpactUtils.findRelevantTests(changedClassPath, javaImpactUtils.findImplementedInterfaces(changedClassPath, repositoryPath), testJavaDirs);
	}

	// Recursively find all src/main/java or src/test/java dirs from a given root
	private Set<Path> findAllJavaSourceDirs(final Path root, final String part) {
		final Set<Path> dirs = new HashSet<>();
		try (final Stream<Path> paths = Files.walk(root)) {
			paths.filter(Files::isDirectory)
					.filter(p -> p.toString().replace(File.separator, "/").endsWith(part))
					.forEach(dirs::add);
		} catch (final IOException e) {
			log.error("Cannot find Java source files", e);
		}

		return dirs;
	}

	private JavaImpactUtils createJavaImpactUtils(final ImpactCheckerConfig impactCheckerConfig) {
		final ParserConfiguration parserConfiguration = new ParserConfiguration();
		parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
		final JavaParser parser = new JavaParser(parserConfiguration);
		return new JavaImpactUtils(parser, impactCheckerConfig);
	}

}
