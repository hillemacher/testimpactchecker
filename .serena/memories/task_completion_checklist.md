# Task Completion Checklist

After finishing any code change, run the following before committing:

## 1. Format
```bash
./gradlew spotlessApply
```
CI will fail on any formatting drift. This must always run before a commit.

## 2. Full verification
```bash
./gradlew check                  # root: unit tests + Spotless check + Checkstyle
./gradlew :plugin:check          # plugin: unit tests + integrationTest + Spotless + Checkstyle
```
Or, for the whole repo at once:
```bash
./gradlew check :plugin:check
```

## 3. If parsing semantics or IndexEntry changed
- Bump `cacheVersion` in `JsonFileTypeIndexCache` to invalidate on-disk caches.

## 4. If plugin was changed
- Run `./gradlew :plugin:integrationTest` explicitly to verify the Gradle TestKit suite.
- Publish locally if testing a consumer project: `./gradlew :plugin:publishToMavenLocal`

## 5. Commit hygiene
- Stage files by name (not `git add -A` or `git add .`).
- Never skip hooks (`--no-verify`).
- Prefer new commits over `--amend`.
