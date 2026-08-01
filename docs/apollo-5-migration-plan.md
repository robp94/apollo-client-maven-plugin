# Apollo Kotlin 5.x migration plan

Living document — update it as steps land or assumptions turn out wrong.

- **Created:** 2026-08-01
- **Fork:** <https://github.com/robp94/apollo-client-maven-plugin> (forked from `aoudiamoncef/apollo-client-maven-plugin`)
- **Current state:** `7.2.0-SNAPSHOT`, targets Apollo **3.8.4**
- **Goal:** target Apollo Kotlin **5.0.1**, and make later Apollo bumps cheap and mostly agent-driven

## Decisions

Recorded so we don't relitigate them. Change them here if we change our minds.

| Topic | Decision | Consequence |
|---|---|---|
| Publishing | **None for now** | Keep `com.github.aoudiamoncef` groupId. No Sonatype namespace, no GPG key, no release pipeline. Consume via `mvn install` or a CI-built artifact. `release.yml` gets disabled, not fixed. |
| Java baseline | **17** | `java.version` 1.8 → 17. Consumers must run Maven on JDK 17+. |
| Removed config options | **Clean break** | Options Apollo deleted are deleted here too. Existing user POMs referencing them will fail with "unknown parameter". Documented in the README. |
| Dependency automation | **Renovate** (Mend-hosted app) | Replaces a hand-rolled version-watch workflow. Free for public repos, runs off our Actions minutes entirely. |
| Claude in CI | **None** | No `claude-code-action`, no API key, no secrets. Claude is invoked on demand (web session or IntelliJ) when a Renovate PR goes red. |

### Why no Claude GitHub Action

`anthropics/claude-code-action` authenticates with an Anthropic API key (pay-per-token) or a cloud
provider. The `CLAUDE_CODE_OAUTH_TOKEN` path for Pro/Max subscribers exists but the token expires in
roughly a day ([issue #727](https://github.com/anthropics/claude-code-action/issues/727)), which
rules it out for a cron job — and it draws from the subscription quota regardless. Revisit if
long-lived subscription tokens ship.

## The gap

The compiler entry point was rewritten between 3.x and 4.x/5.x. This is not a rename job.

| Apollo 3.8.4 | Apollo 5.0.1 |
|---|---|
| `com.apollographql.apollo3.*` | `com.apollographql.apollo.*` |
| `ApolloCompiler.write(Options(...))`, ~30 flat fields | `ApolloCompiler.buildSchemaAndOperationsSources(...)` returning `SourceOutput`, then `.writeTo(dir, deleteDirectoryFirst, codegenSymbolsFile)` |
| `File` inputs | `InputFile(file, normalizedPath)` — `normalizedPath` drives package naming |
| one `Options` monolith | `CodegenSchemaOptions` + `IrOptions` + `CodegenOptions` (built via `buildIrOptions(...)` / `buildCodegenOptions(...)`) |
| `OperationOutputGenerator` / `OperationIdGenerator` | **removed** — `OperationIdsGenerator` parameter, or `ApolloCompilerPlugin` |
| `scalarMapping: Map<String, ScalarInfo>` | `scalarTypeMapping` + `scalarAdapterMapping`, two `Map<String, String>` |
| `warnOnDeprecatedUsages: Boolean` | `issueSeverities: Map<String, IssueSeverity>` |
| `packageNameGenerator = PackageNameGenerator.Flat(x)` | `packageName` / `rootPackageName` on `CodegenOptions` |
| `testDir`, `debugDir`, `generateTestBuilders` | **removed** in v4 |
| `generateResponseFields` | **removed** |
| introspection helpers in `apollo-compiler` | moved to `apollo-ast`, `com.apollographql.apollo.ast.introspection` |
| `operationManifestFile` on `Options` | parameter of `buildSchemaAndOperationsSources` |
| `ApolloCompiler.NoOpLogger` (public) | **private** in v5 — supply our own `ApolloCompiler.Logger` bridging to the Maven log |

Build-chain fallout: `apollo-compiler` 5 needs Kotlin 2.x, and pulls in `kotlinx-serialization`,
KotlinPoet and JavaPoet. Our POM pins Kotlin 1.9.21, passes the long-obsolete `-Xuse-ir` flag, and
uses `dokka-maven-plugin` 0.9.17 — none of which survive a Kotlin 2 bump.

## Stage 0 — De-risk

Gates everything else. Cheap; do it first.

- [x] Throwaway Maven project depending on `com.apollographql.apollo:apollo-compiler:5.0.1`, run `mvn dependency:tree`.
  - [x] **Does `apollo-ast` resolve under plain Maven?** — **Yes, no workaround needed.** Apollo's published POM already points at the platform artifacts: `apollo-ast-jvm` and `apollo-annotations-jvm`. The KMP/Gradle-module-metadata concern does not apply. This was the project's biggest unknown; it is closed.
  - [x] Confirm Java 17 is sufficient — **settled by decision, not measured.** No MCP tool exposes a jar's class-file major version, and it is moot: 17 clears any plausible floor for a library whose own stdlib is Kotlin 1.9.
- [x] Maven wrapper added to the repo (Maven 3.9.16). Local toolchain is JDK 21.
- [ ] Check out the sibling `apollo-kotlin` clone at tag **`v5.0.1`**, not `main`. It currently sits on `5.0.2-SNAPSHOT`; reading snapshot sources while compiling against a release is a subtle way to lose an afternoon.

### Resolved dependency tree (2026-08-01)

```
com.apollographql.apollo:apollo-compiler:5.0.1
├─ org.jetbrains.kotlin:kotlin-stdlib:1.9.0
├─ com.apollographql.apollo:apollo-ast-jvm:5.0.1
│  ├─ com.squareup.okio:okio-jvm:3.16.2
│  ├─ com.apollographql.apollo:apollo-annotations-jvm:5.0.1
│  └─ dev.drewhamilton.poko:poko-annotations-jvm:0.21.3 (runtime)
├─ com.squareup:kotlinpoet-jvm:2.2.0
├─ com.squareup:javapoet:1.13.0
├─ org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1
└─ org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1 (runtime)
```

**Apollo 5 requires Kotlin 2.2+.** An earlier reading of this tree concluded the opposite, because
`apollo-compiler` declares `kotlin-stdlib:1.9.0` and pins its own language level to `KOTLIN_1_9`.
Both are misleading: the language-level pin governs *source* features, and the declared stdlib is
only a floor. What actually matters is the **binary metadata version** of the jars, which the
dependency tree does not show:

| Artifact | Metadata version |
|---|---|
| `apollo-ast-jvm`, `apollo-api-jvm` | 2.1.0 |
| `kotlinpoet-jvm` 2.2.0 | 2.1.0 |
| `kotlinx-serialization-core-jvm` 1.8.1 | 2.1.0 |
| `okio-jvm` 3.16.2 | **2.2.0** |

Kotlin 1.9.21 reads metadata up to 2.0.0 only, so it fails on all of them. **okio pushes the floor
to Kotlin 2.2** — 2.1 is not sufficient. `kotlin.version` is therefore 2.2.0, and `-Xuse-ir`
(removed in Kotlin 2) is gone.

Lesson worth keeping: `dependency:tree` cannot answer "which Kotlin do I need". Only a compile can.

**dokka 0.9.17 is now a latent problem** — it will not work under Kotlin 2, but it only runs in the
`publication` profile, which activates on `-Drelease`. Since we are not publishing, it stays
dormant. It must be fixed before any publishing decision is revisited.

## Stage 1 — Port existing features to 5.0.1

Each step should compile before starting the next.

- [x] **POM** — `apollo.version` → `5.0.1`; groupId `com.apollographql.apollo3` → `com.apollographql.apollo`; `java.version` → 17; `apollo-ast` → `apollo-ast-jvm` (matching what apollo-compiler's own POM depends on); `kotlin.version` → 2.2.0; `-Xuse-ir` removed. **Verified: all dependencies resolve and all metadata is readable.**
  - [ ] Still open, deferred to the cleanup step: drop moshi if apollo-ast's JSON reader covers `SchemaDownloader`; check whether `apollo-api-jvm` is still needed by the plugin module at all; fix dokka before any publishing decision.
- [x] **`util/ConfigUtils.kt`, `util/SchemaDownloader.kt`** — both compile clean.
  - `ConfigUtils.convert()` had no callers anywhere in the repo and was deleted rather than migrated. It was also the hardest piece to port, chaining four introspection helpers that all moved or changed.
  - `apollo3.compiler.fromJson` no longer exists in v5. The GraphOS registry response is now parsed with the Jackson `ObjectMapper` already present in that file, which also removes a dependency on an Apollo internal and makes the code immune to future Apollo churn.
  - `APOLLO_VERSION` moved to `com.apollographql.apollo.compiler`.
- [ ] **`util/ConfigUtils.kt`, `util/SchemaDownloader.kt`** — introspection imports → `com.apollographql.apollo.ast.introspection`. Small, isolated, unblocks the rest.
- [x] **`config/CompilerParams.kt`, `config/CompilationUnit.kt`, `config/Codegen.kt`, `util/BuildDirLayout.kt`** — clean break done. Removed: `generateTestBuilders`, `generateDataBuilders`, `generateResponseFields`, `operationIdGeneratorClass`, `generateApolloMetadata`, `metadataFiles`, `metadataOutputFile`, `schemaPackageName`, `rootFolders`, `generateKotlinModels`, `logger`, `testDirectory`, `debugDirectory`. `Codegen.COMPATIBILITY` is gone (Apollo 4 removed the compat model); the enum labels now come from the Apollo constants so an upstream rename fails at compile time.
- [x] **`GraphQLClientMojo.execute()`** — migrated to `buildSchemaAndOperationsSources(...)` → `SourceOutput.writeTo(...)`.
  - **Gotcha worth remembering:** `CodegenOptions.validate()` throws if an option belonging to the *other* target language is set at all. Java-only and Kotlin-only options must be passed as `null`, not `false`, when they do not apply.
  - `warnOnDeprecatedUsages` is now expressed through `issueSeverities`. Warn is already the default, so a severity is only supplied when the user opts out.
  - Added `MavenApolloLogger`, since `ApolloCompiler.NoOpLogger` is private in v5. Apollo's warnings now reach the Maven log instead of being discarded.
- [x] **`apollo-client-maven-plugin-tests`** — migrated. `targetLanguage` moved `KOTLIN_1_5` → `KOTLIN_1_9` (`KOTLIN_1_5` is a `DeprecationLevel.ERROR` symbol in v5).
- [x] **ktlint-maven-plugin 1.16.0 → 3.5.0** — 1.16.0 embeds kotlin-compiler-embeddable 1.8.0, which throws `ExceptionInInitializerError` on JDK 21. Its newer style rules reformatted the sources in the same commit.

### Status: the full reactor builds green and all 3 tests pass

Codegen runs, the generated Kotlin compiles, both queries execute against a live Undertow GraphQL
server, and the persisted query manifest is written.
- [ ] **Golden-file codegen test** — snapshot generated sources for the books schema and diff on every build. Highest-leverage item for the "future bumps are cheap" goal: it is what makes each Renovate PR self-verifying.
- [ ] **README** — plugin ↔ Apollo version table; note that the fork is unpublished and built with `mvn install`; list the removed configuration options.

### Deferred to stage 2

Not in scope for the first pass — port what exists before adding what is new.

- Multi-module support: `CodegenMetadata`, `generateApolloMetadata`, `metadataFiles`
- `ApolloCompilerPlugin` (the replacement for custom operation-ID generators)
- Data builders (`generateDataBuilders`)
- Foreign schemas / `@link` options
- Kotlin `generateInputBuilders`, `jsExport`, `generateApolloEnums`

## Stage 2 — Renovate and CI

- [ ] Install the **Mend-hosted Renovate GitHub App** (free for public repos, runs on Mend infrastructure — costs no Actions minutes).
- [ ] `renovate.json`:
  - [ ] Group all `com.apollographql.apollo:*` artifacts into one PR, so an Apollo bump is a single reviewable change with CI attached.
  - [ ] Verify Renovate resolves `<version>${apollo.version}</version>` back to the `apollo.version` property and bumps it there.
  - [ ] Also covers the ~25 stale Maven plugin version properties, Kotlin, and `actions/*` in workflows.
- [ ] **`build.yml`** — currently Java 8 on the deprecated `adopt` distribution. Retarget to `temurin` 17, run `mvn verify`.
- [ ] **`release.yml`** — disable, given no publishing.

Workflow: green Renovate PR → merge. Red → open it in Claude (web session or IntelliJ).

**Honest limitation:** for a *major* Apollo bump the diff will be red and no tooling changes that —
the compiler API moves between majors. What the golden tests and the skill file buy is that the
failure arrives as a precise, reproducible compile error with a documented procedure attached,
instead of an open-ended investigation.

## Stage 3 — Claude enablement

- [ ] **`CLAUDE.md`** — module layout, build commands, and the key orientation: *the entire Apollo surface we depend on is `ApolloCompiler.kt` + `Options.kt` + `SourceOutput.kt`; read them at the matching tag before changing anything.*
- [ ] **`.claude/skills/upgrade-apollo/SKILL.md`** — the repeatable recipe: bump property → build → read errors → diff `Options.kt` between old and new Apollo tags → fix the mapping → run golden tests → update the README table.
- [ ] **`.claude/settings.json`** — allowlist `mvn`, `git`, `gh` so upgrade runs don't stall on permission prompts.
- [ ] Document how to put Apollo sources in front of the agent: sibling checkout locally, sources-jar fetch script for cloud sessions.

## Reference

- Compiler entry point: `apollo-kotlin/libraries/apollo-compiler/src/main/kotlin/com/apollographql/apollo/compiler/ApolloCompiler.kt`
- Options: `.../compiler/Options.kt`
- Output writing: `.../compiler/codegen/SourceOutput.kt`
- Introspection: `apollo-kotlin/libraries/apollo-ast/src/*/kotlin/com/apollographql/apollo/ast/introspection/`
- [Apollo Kotlin 5.0 migration guide](https://www.apollographql.com/docs/kotlin/migration/5.0)
- [Apollo Kotlin releases](https://github.com/apollographql/apollo-kotlin/releases)
