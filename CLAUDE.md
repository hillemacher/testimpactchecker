# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, test, format

Java 21 toolchain, Gradle 9.4.0 via the wrapper. Two Gradle modules: the root project (CLI / library) and `:plugin` (Gradle plugin).

- `./gradlew build` — compile + tests + shaded CLI jar (`build/libs/TestImpactChecker-CLI-*.jar`).
- `./gradlew check` — full verification (tests + Spotless + Checkstyle); same suite CI runs.
- `./gradlew spotlessApply` — apply Google Java Format (1.25.2). CI fails on formatting drift, so run before committing.
- `./gradlew test` — root unit tests (JUnit 5 + Mockito + AssertJ).
- `./gradlew test --tests <fqcn>` — single test class. JUnit Platform is enabled, so `--tests '*Foo*.bar'` patterns also work.
- `./gradlew :plugin:test` — plugin unit tests.
- `./gradlew :plugin:integrationTest` — plugin TestKit suite (`plugin/src/integrationTest`). Wired into `:plugin:check`.
- `./gradlew :plugin:publishToMavenLocal` — publish the plugin to `~/.m2/` for local consumer projects (the plugin is not yet on the Gradle Plugin Portal).
- `./gradlew shadowJar` — build the standalone CLI jar without running the rest of the build.

Run the CLI directly: `java -jar build/libs/TestImpactChecker-CLI-1.0-SNAPSHOT.jar -p <projectPath> -c <configPath>`. Logs go to stderr; structured output goes to stdout. Use `--debug` for DEBUG-level diagnostics.

## Architecture

### Modules

- **Root project** (`src/main/java`) — the analysis library and the `TestImpactCheckerCli` entry point. Built into a shaded CLI jar via `com.gradleup.shadow`.
- **`:plugin`** (`plugin/src/main/java`) — Gradle plugin (`io.github.hillemacher.testimpactchecker`) that depends on the root project and exposes the same analysis as a Gradle task plus a test-filter wiring action. The plugin uses `java-gradle-plugin` and `maven-publish`; consumer-facing tests live in the `integrationTest` source set and use `gradleTestKit()`.

### Analysis pipeline

The main orchestrator is `TestImpactChecker` (root package). Its `detectImpactReportData` method drives the full run; `cli.TestImpactCheckerCli` wraps it for command-line use.

The pipeline (under `java/analysis/`) runs in this order:

1. `ChangedClassLocator` + `git.GitImpactUtils` (JGit) — identify changed `.java` files between `baseRef` and `targetRef` (plus uncommitted changes).
2. `ChangedTypeSeedResolver` — convert changed files into seed FQCNs (`ChangedTypeSeedData`), including interfaces implemented by changed classes.
3. `MainSourceIndexBuilder` — parse all `src/main/java` files with JavaParser to produce a `TypeDependencyIndex`. This step is cache-aware (see below).
4. `TransitiveImpactPropagator` — for `analysisMode = TRANSITIVE`, walks the reverse dependency graph up to `maxPropagationDepth` to expand the impacted set. `DIRECT` skips this and uses the seeds directly.
5. `TestTypeUsageExtractor` + `TestMockUsageExtractor` — parse `src/test/java`, find tests carrying any of the configured `annotations`, and record which types they reference vs. only mock.
6. `TestImpactEvaluator` — combine the impacted-type set with test usages and the active `MockPolicy` (`CURRENT` filters causes when the changed class itself is mocked; `FILTER_MOCKED_PATHS` filters causes only when every witness path back to the seed is blocked by mocked types) to produce `ImpactAnalysisResult`.

`ImpactAnalysisEngine` is the in-package orchestrator that wires those steps together; `TestImpactChecker` is the public façade that also handles cache lifecycle.

### Caching

Main-source parsing is the hot path, so `MainSourceIndexBuilder` consults a SHA-256-keyed per-file cache (`java/analysis/cache/`). On disk it lives at `<project>/.testimpactchecker/cache/type-index.v1.json` by default, overridable via the config `cacheDirectoryPath` (the Gradle plugin redirects it to `build/test-impact/cache/` so `clean` wipes it).

- `TestImpactChecker.CacheMode` controls cache behavior: `ENABLED` (default), `DISABLED` (`--no-cache`), `CLEAR_THEN_USE` (`--clear-cache`), and `VERIFY` (`--verify-cache`, runs both cached + uncached and asserts equality).
- `JsonFileTypeIndexCache` persists entries; `NoOpTypeIndexCache` is the disabled variant; `FileHasher` produces the cache keys. A `cacheVersion` in the JSON invalidates the whole cache on tool upgrades.
- When changing parsing semantics or anything that affects `IndexEntry`, bump the cache version so existing on-disk caches are invalidated.

### Output and reporting

- `cli/output/` defines `OutputFormat` (`human`, `json`, `gradle-filter`, `junit-includes`) and a formatter per variant. `--format` is repeatable; `--format-out` pairs by position. Only one format may write to stdout per run; `OutputFormatters` enforces that.
- `report/` builds the optional self-contained HTML report. `ImpactReportMapper` produces the report model, `HtmlImpactReportTemplateModel` shapes it for the templates, `HtmlImpactReportRenderer` renders precompiled `jte` templates from `src/main/jte/report/` (compiled by the `gg.jte.gradle` plugin and packaged into the jar), and `ImpactGraph*` / `ImpactGraphSvgRenderer` produce the embedded overview + per-cause SVG graphs. Static assets the templates inline live under `src/main/resources/report/`.
- Graph caps are intentionally fixed in code (12 causes, 28 impacted types, 40 tests, 80 nodes total) for deterministic CI artifacts — change them deliberately.

### Gradle plugin (`:plugin`)

`TestImpactCheckerPlugin` registers the `testImpact` extension (`TestImpactCheckerExtension` — lazy `Property`/`DirectoryProperty`/`RegularFileProperty` mirroring the CLI config) and the `testImpactCheck` task (`TestImpactCheckTask`, annotated `@DisableCachingByDefault`). The extension's `applyTo(test)` invokes `ApplyImpactedFilterAction` to wire impacted FQCNs into `test.filter.includeTestsMatching`; an empty impacted set means `test` exits successfully without running.

## Conventions

- Formatter: Google Java Format via Spotless (`./gradlew spotlessApply`). Also formats trailing whitespace + newline-at-EOF for `src/main/resources/**/*.{js,css}` so embedded report assets stay consistent.
- Style: Checkstyle 10.21.3 with the config at `config/checkstyle/checkstyle.xml` (generated jte sources and `**/gg/jte/generated/**` are excluded). HTML reports under `build/reports/checkstyle/`.
- Lombok is used in both modules; the annotation processor is wired in `dependencies` — keep it there when adding new source sets.
- `slf4j-simple` is the bundled logging backend. `TestImpactCheckerCli.configureLogging` sets system properties before the first logger call, so don't initialize loggers in `main` before that runs.
