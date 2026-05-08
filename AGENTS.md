# AGENTS.md

## Project Overview

This is a Java-based command-line tool called "Test Impact Checker" that analyzes Git changes and determines which test classes are impacted by those changes.

## Key Commands

- `./gradlew build` - Build the project and create the CLI JAR
- `./gradlew check` - Run all checks (tests, formatting, style)
- `./gradlew spotlessApply` - Apply Google Java Format
- `./gradlew test` - Run unit tests

## Build & Test Setup

- Requires Java 21 toolchain
- Uses Gradle 9.4.0 via wrapper
- Project is structured with standard Maven/Gradle directories (`src/main/java`, `src/test/java`)
- Tests are written with JUnit 5 and Mockito

## Architecture Notes

- Main entry point: `io.github.hillemacher.testimpactchecker.cli.TestImpactCheckerCli`
- Uses JGit for Git operations
- Uses JavaParser for source code analysis
- Uses jte for precompiled HTML report templates
- Implements impact analysis with two modes: DIRECT and TRANSITIVE
- Supports incremental caching of parsed source files

## Testing Quirks

- Tests use JUnit 5 with Mockito for mocking
- Integration tests may require Git repository setup
- Test output is formatted in multiple ways (human, JSON, gradle-filter, junit-includes)
- Cache behavior can be controlled with --no-cache, --clear-cache, and --verify-cache flags

## CI/CD Integration

- Can be integrated into CI pipelines to run only impacted tests
- Supports generating HTML reports for test impact visualization
- Reports can be used as artifacts in CI systems
- The Gradle plugin provides direct integration without manual JAR management

## Output Formats

- `human` (default): Human-readable grouped table output
- `json`: Structured JSON with impacted tests and stats
- `gradle-filter`: Test class names for Gradle integration
- `junit-includes`: Surefire-style include globs for JUnit testing

## Configuration

The tool requires a JSON configuration file specifying:

- annotations: List of annotation names identifying test classes
- baseRef and targetRef: Git references to compare
- analysisMode: DIRECT or TRANSITIVE
- mockPolicy: CURRENT or FILTER_MOCKED_PATHS
