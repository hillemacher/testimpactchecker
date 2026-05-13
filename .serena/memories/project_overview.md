# Project Overview: TestImpactChecker

## Purpose
TestImpactChecker analyzes which JUnit tests are impacted by Git changes (diff between two refs, plus uncommitted changes). It avoids running the entire test suite by detecting only the tests that actually depend on changed classes.

## Tech Stack
- **Java 21** with **Gradle 9.4.0** (via wrapper)
- **JavaParser 3.27.0** — parses `src/main/java` to build a type-dependency index
- **JGit 7.3.0** — identifies changed `.java` files from Git history
- **Jackson 2.19.1** — serializes/deserializes the file-based cache and JSON output
- **Lombok 1.18.38** — reduces boilerplate; annotation processor wired in `dependencies`
- **jte 3.2.3** — pre-compiled templates for the HTML impact report
- **slf4j-simple 2.0.17** — logging backend (stderr); configured before first logger call
- **Apache Commons** (IO, Lang3, CLI) — utilities and CLI argument parsing
- **JUnit 5 + Mockito 5 + AssertJ** — test stack in both modules
- **Spotless 6.25.0** + **Google Java Format 1.25.2** — code formatting
- **Checkstyle 10.21.3** — style enforcement (`config/checkstyle/checkstyle.xml`)
- **Shadow plugin** (`com.gradleup.shadow`) — builds a shaded CLI jar

## Modules
1. **Root project** — analysis library + `TestImpactCheckerCli` entry point → shaded CLI jar
2. **`:plugin`** — Gradle plugin (`io.github.hillemacher.testimpactchecker`) that wraps the library as a `testImpactCheck` task
