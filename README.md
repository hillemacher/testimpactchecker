# Test Impact Checker

**Test Impact Checker** is a command-line tool for Java projects that analyzes changes in your Git repository and intelligently determines which test classes are relevant to those changes. This enables faster, more focused testing by only running tests that are likely to be affected by recent code modifications.

## Features

- Detects changed Java classes and their implemented interfaces in your repository using Git diffs.
- Scans your test sources to find tests annotated with configurable annotations (e.g., `@ContextConfiguration`).
- Identifies tests that reference changed classes or their interfaces, excluding cases where changes are only used as mocks.
- Supports both committed and uncommitted changes.
- Outputs impacted test files together with the changed class causes, making it easy to integrate with CI pipelines.

## How It Works

1. **Discover Source and Test Directories:** Recursively locates all `src/main/java` and `src/test/java` directories in your project.
2. **Detect Changes:** Uses JGit to find changed Java files between two Git refs (branches, tags, or commits), as well as any uncommitted changes.
3. **Analyze Test Coverage:** Parses test classes for relevant annotations and references to changed classes/interfaces, filtering out tests that only mock the changes.
4. **Report Impact:** Outputs relevant test files and the changed classes that caused each test to be included.

## Usage

### Command Line

Run the tool with the path to your project and the configuration JSON file as arguments. Use the help flag for more information about available options.

#### Arguments

- `-p <projectPath>` or `--project <projectPath>`: Path to the root of the project to analyze. **(Required)**
- `-c <configPath>` or `--config <configPath>`: Path to the JSON configuration file. **(Required)**
- `--html-report <path-or-directory>`: Optional output path for a static HTML report. If a directory is provided, the report file name defaults to `impact-report.html`. This overrides the config value `htmlReportOutputPath` when both are set.
- `-d` or `--debug`: Enable debug logging with detailed diagnostics. *(Optional)*
- `-h` or `--help`: Show help and usage information.

### Example Output

The tool prints grouped output with impacted tests and their causes to standard output:

```text
----------------- ----------------- -----------------
Relevant tests and impact causes:
foo/src/test/java/TestA.java
  caused by: A
foo/src/test/java/TestB.java
  caused by: B, C
----------------- ----------------- -----------------
```

### Report Output

When `--html-report` is set, the checker additionally writes a static HTML artifact that contains:

- A run metadata section with the effective project path, Git refs, annotations, analysis settings, and config path
- Summary cards (impacted tests, unique causes, average causes per test)
- Impacted tests and their causes
- Top causes sorted by impacted test count
- A static graph section with a simplified overview graph plus collapsible per-cause detail graphs

Path behavior:

- If the value ends with `.html`, it is treated as an output file path.
- Otherwise, it is treated as a directory and `impact-report.html` is appended.
- Relative paths are resolved from the provided project root (`--project`), not from the config file location.

CI recommendation:

- Use `${project}/reports/impact-report.html` as your artifact path.
- The report renders the generation timestamp in the executing machine's local timezone and includes the UTC timestamp in parentheses.
- The execution zone shown in the report comes from the machine running the checker (`ZoneId.systemDefault()`), so local runs and CI may display different zones.
- The graph is intentionally focused/capped for deterministic CI artifacts, and the overview graph hides lower-signal edges for readability.
- Use the per-cause detail graphs in the report when you need to inspect exact edge relationships for a specific changed cause.
- Default caps for the graph are fixed in code (`12` causes, `28` impacted types, `40` tests, `80` total nodes).

## Configuration

The tool expects a JSON configuration file with the following fields:

- **annotations**: List of annotation names (without @) used to identify relevant test classes.
- **baseRef**: The base Git reference for comparison (e.g., `refs/heads/main`).
- **targetRef**: The target Git reference for comparison (e.g., `HEAD`).
- **analysisMode**: `DIRECT` (default) or `TRANSITIVE`.
- **maxPropagationDepth**: Max reverse dependency traversal depth for `TRANSITIVE` mode. Defaults to `2`.
- **mockPolicy**: `CURRENT` (default) or `FILTER_MOCKED_PATHS`.
- **htmlReportOutputPath** *(optional)*: Path or directory for HTML report output when `--html-report` is not supplied. Relative values are resolved from `--project`.

### Compare a Specific Commit with a Target

You can compare a specific commit against any target ref by setting `baseRef` to the commit hash
and `targetRef` to the desired branch, tag, or commit. For example, to compare commit
`abc1234` against `main`:

```json
{
  "annotations": ["ContextConfiguration"],
  "baseRef": "abc1234",
  "targetRef": "refs/heads/main",
  "analysisMode": "TRANSITIVE",
  "maxPropagationDepth": 2,
  "mockPolicy": "FILTER_MOCKED_PATHS",
  "htmlReportOutputPath": "reports/impact-report.html"
}
```

### Analysis Modes

- `DIRECT` (default): Matches tests that directly reference changed classes or interfaces implemented by changed classes.
- `TRANSITIVE`: Builds a reverse dependency graph from `src/main/java` and propagates impact to dependents up to `maxPropagationDepth`.

### Mock Policies

- `CURRENT` (default): Excludes a cause when a changed class itself is mocked.
- `FILTER_MOCKED_PATHS`: In transitive analysis, excludes a cause only when all witness paths from referenced impacted type back to the changed seed are blocked by mocked types.

## Requirements

- Java 21 or later
- A Git repository (with JGit-compatible structure)
- Java source files organized under standard Maven/Gradle directories (`src/main/java`, `src/test/java`)

## Integration

Integrate this tool into your CI pipeline to optimize test runs and accelerate feedback for developers. For example, you can use its output to trigger only impacted tests in your test runner.

## Building

Clone the repository and build with your preferred Java build tool (e.g., Maven or Gradle).

## Contributing

Use the Gradle wrapper for local verification so formatting, style, and tests match CI.

Format Java sources before committing:

```bash
./gradlew spotlessApply
```

Run the full verification suite before opening a pull request:

```bash
./gradlew check
```

Pull requests are expected to pass the GitHub Actions CI workflow, and repository admins can
enforce that by marking the workflow check as required in branch protection for `main`.

## Dependencies

Key dependencies include:

- JGit (for Git operations)
- JavaParser (for Java source analysis)
- Lombok (for boilerplate reduction)
- Jackson (for JSON config parsing)
- Apache Commons libraries (`commons-io`, `commons-lang3`)

## Logging

The application uses SLF4J for logging.

- By default, the tool logs process progress at `INFO` level.
- Use `--debug` to enable detailed diagnostics at `DEBUG` level.
- Logs are written to `stderr`, while the structured impact report is written to `stdout`.

Example:

```bash
java -jar TestImpactChecker-CLI-1.0-SNAPSHOT.jar \
  --project /path/to/repo \
  --config /path/to/config.json \
  --debug
```

## License

This project is licensed under the [Apache License, Version 2.0](LICENSE).

## Contributing

Pull requests and issues are welcome! Please open an issue to discuss your suggestions or bug reports.
