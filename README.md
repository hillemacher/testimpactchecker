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

## Configuration

The tool expects a JSON configuration file with the following fields:

- **annotations**: List of annotation names (without @) used to identify relevant test classes.
- **baseRef**: The base Git reference for comparison (e.g., `refs/heads/main`).
- **targetRef**: The target Git reference for comparison (e.g., `HEAD`).

### Compare a Specific Commit with a Target

You can compare a specific commit against any target ref by setting `baseRef` to the commit hash
and `targetRef` to the desired branch, tag, or commit. For example, to compare commit
`abc1234` against `main`:

```json
{
  "annotations": ["ContextConfiguration"],
  "baseRef": "abc1234",
  "targetRef": "refs/heads/main"
}
```

## Requirements

- Java 21 or later
- A Git repository (with JGit-compatible structure)
- Java source files organized under standard Maven/Gradle directories (`src/main/java`, `src/test/java`)

## Integration

Integrate this tool into your CI pipeline to optimize test runs and accelerate feedback for developers. For example, you can use its output to trigger only impacted tests in your test runner.

## Building

Clone the repository and build with your preferred Java build tool (e.g., Maven or Gradle).

## Dependencies

Key dependencies include:

- JGit (for Git operations)
- JavaParser (for Java source analysis)
- Lombok (for boilerplate reduction)
- Jackson (for JSON config parsing)
- Apache Commons libraries (`commons-io`, `commons-lang3`)

## Logging

The application uses SLF4J for logging. Log output includes steps, errors, and relevant debug information.

## License

This project is licensed under the [Apache License, Version 2.0](LICENSE).

## Contributing

Pull requests and issues are welcome! Please open an issue to discuss your suggestions or bug reports.
