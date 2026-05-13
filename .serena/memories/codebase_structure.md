# Codebase Structure

## Root layout
```
testimpactchecker/
├── build.gradle          # root module (library + CLI)
├── settings.gradle
├── src/
│   ├── main/java/io/github/hillemacher/testimpactchecker/
│   │   ├── TestImpactChecker.java          # public façade; drives full run; manages cache lifecycle
│   │   ├── ImpactDetectionReportData.java  # top-level result model
│   │   ├── cli/
│   │   │   ├── TestImpactCheckerCli.java   # main() entry point; configures logging
│   │   │   └── output/                     # OutputFormat enum + per-format formatters
│   │   ├── config/                         # config model (baseRef, targetRef, analysisMode, etc.)
│   │   ├── git/
│   │   │   └── GitImpactUtils.java         # JGit helpers to find changed .java files
│   │   ├── java/analysis/
│   │   │   ├── ImpactAnalysisEngine.java   # in-package orchestrator wiring the pipeline steps
│   │   │   ├── ChangedClassLocator.java
│   │   │   ├── ChangedTypeSeedResolver.java
│   │   │   ├── MainSourceIndexBuilder.java # parses src/main/java → TypeDependencyIndex (cache-aware)
│   │   │   ├── TransitiveImpactPropagator.java
│   │   │   ├── TestTypeUsageExtractor.java
│   │   │   ├── TestMockUsageExtractor.java
│   │   │   ├── TestImpactEvaluator.java
│   │   │   └── cache/                      # SHA-256-keyed per-file cache (JsonFileTypeIndexCache, NoOpTypeIndexCache, FileHasher)
│   │   └── report/                         # HTML report (ImpactReportMapper, HtmlImpactReportRenderer, ImpactGraph*)
│   ├── main/jte/report/                    # jte templates for the HTML report
│   └── main/resources/report/              # static assets inlined by the HTML report
│
├── plugin/
│   ├── build.gradle      # :plugin module
│   └── src/
│       ├── main/java/io/github/hillemacher/testimpactchecker/gradle/
│       │   ├── TestImpactCheckerPlugin.java     # registers extension + task
│       │   ├── TestImpactCheckerExtension.java  # lazy Property/DirectoryProperty/RegularFileProperty config
│       │   ├── TestImpactCheckTask.java         # @DisableCachingByDefault Gradle task
│       │   └── ApplyImpactedFilterAction.java   # wires FQCNs into test.filter.includeTestsMatching
│       └── integrationTest/                     # Gradle TestKit suite (fixture-project inside resources/)
└── config/checkstyle/checkstyle.xml
```

## Analysis pipeline order (in ImpactAnalysisEngine)
1. `ChangedClassLocator` + `GitImpactUtils` → changed `.java` files (baseRef…targetRef + uncommitted)
2. `ChangedTypeSeedResolver` → seed FQCNs (`ChangedTypeSeedData`), incl. implemented interfaces
3. `MainSourceIndexBuilder` → `TypeDependencyIndex` (cache-aware; SHA-256 keyed per file)
4. `TransitiveImpactPropagator` → expand impacted set (TRANSITIVE mode; DIRECT skips this)
5. `TestTypeUsageExtractor` + `TestMockUsageExtractor` → which tests reference / only-mock each type
6. `TestImpactEvaluator` + `MockPolicy` → `ImpactAnalysisResult`

## Cache
- On disk: `<project>/.testimpactchecker/cache/type-index.v1.json` (default); plugin redirects to `build/test-impact/cache/`
- `TestImpactChecker.CacheMode`: `ENABLED`, `DISABLED`, `CLEAR_THEN_USE`, `VERIFY`
- **Bump `cacheVersion`** whenever parsing semantics or `IndexEntry` structure changes

## Output formats
`human`, `json`, `gradle-filter`, `junit-includes` — configured via `--format` / `--format-out`; only one format may write to stdout per run.

## HTML report graph caps (fixed in code, change deliberately)
12 causes · 28 impacted types · 40 tests · 80 nodes total
