# Suggested Commands

## Build
```bash
./gradlew build                          # compile + tests + shaded CLI jar
./gradlew shadowJar                      # shaded CLI jar only (skips other checks)
```

## Full verification (what CI runs)
```bash
./gradlew check                          # tests + Spotless + Checkstyle (root)
./gradlew :plugin:check                  # plugin tests + integrationTest + Spotless + Checkstyle
```

## Testing
```bash
./gradlew test                           # root unit tests (JUnit 5)
./gradlew test --tests 'com.example.FooTest'   # single test class (FQCN or wildcard pattern)
./gradlew :plugin:test                   # plugin unit tests
./gradlew :plugin:integrationTest        # plugin Gradle TestKit suite
```

## Formatting & linting
```bash
./gradlew spotlessApply                  # apply Google Java Format (run before committing!)
./gradlew spotlessCheck                  # check formatting without applying
# Checkstyle reports: build/reports/checkstyle/
```

## Publishing the plugin locally
```bash
./gradlew :plugin:publishToMavenLocal    # publishes to ~/.m2/ for local consumer projects
```

## Running the CLI
```bash
java -jar build/libs/TestImpactChecker-CLI-1.0-SNAPSHOT.jar \
  -p <projectPath> -c <configPath>
# --debug       DEBUG-level diagnostics (to stderr)
# --no-cache    disable cache
# --clear-cache clear then use cache
# --verify-cache run cached + uncached and assert equality
# --format <fmt> output format: human|json|gradle-filter|junit-includes (repeatable)
# --format-out <file> paired with --format by position
```

## System utilities (macOS / Darwin)
```bash
git, ls, find, grep, sed, awk, cat, open, pbcopy, pbpaste
# Note: BSD variants on macOS differ from GNU — e.g., sed -i '' (not sed -i)
```
