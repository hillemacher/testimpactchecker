# Style and Conventions

## Formatting
- **Google Java Format 1.25.2** via Spotless — run `./gradlew spotlessApply` before every commit.
- Spotless also enforces trailing-whitespace removal + newline-at-EOF for `src/main/resources/**/*.{js,css}`.
- Checkstyle 10.21.3 config: `config/checkstyle/checkstyle.xml`. Generated jte sources (`**/gg/jte/generated/**`) are excluded.

## Lombok
- Used in **both** modules. Annotation processor must be declared in `dependencies` (both `compileOnly`/`annotationProcessor` and `testCompileOnly`/`testAnnotationProcessor`).
- Keep that wiring when adding new source sets (e.g., integrationTest).

## Logging
- Backend: `slf4j-simple` (bundled).
- `TestImpactCheckerCli.configureLogging()` sets system properties **before** the first logger call — do **not** initialize loggers in `main()` before that method runs.

## Comments
- Only add comments when the **WHY** is non-obvious (hidden constraint, workaround, subtle invariant).
- Do **not** narrate what the code does; rely on well-named identifiers.
- Do not reference the current task or PR in comments.

## Cache versioning
- When changing parsing semantics or the `IndexEntry` structure, **bump `cacheVersion`** in `JsonFileTypeIndexCache` so existing on-disk caches are invalidated automatically.

## Report graph caps
- Fixed in code: 12 causes, 28 impacted types, 40 tests, 80 nodes total.
- Change these deliberately (they exist to produce deterministic CI artifacts).

## Naming
- Base package: `io.github.hillemacher.testimpactchecker`
- Plugin package: `io.github.hillemacher.testimpactchecker.gradle`
- Class names are descriptive and use the domain vocabulary (e.g., `ChangedTypeSeedResolver`, `TransitiveImpactPropagator`).

## Design patterns
- Public façade (`TestImpactChecker`) over internal orchestrator (`ImpactAnalysisEngine`).
- `CacheMode` enum controls behaviour; `NoOpTypeIndexCache` is the disabled variant (null-object pattern).
- `OutputFormatters` enforces the one-stdout-format-per-run constraint.
- Plugin uses lazy Gradle `Property`/`DirectoryProperty`/`RegularFileProperty` for configuration.
- `@DisableCachingByDefault` on `TestImpactCheckTask` — Gradle task caching is intentionally off.
