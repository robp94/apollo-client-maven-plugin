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

**Finding that changes Stage 1:** the tree pulls **kotlin-stdlib 1.9.0**, not 2.x. That matches
`apollo-compiler`'s own build, which pins language level `KOTLIN_1_9`. **Apollo 5 does not force a
Kotlin 2 upgrade.** Keep `kotlin.version` at 1.9.21 for the first pass so the Apollo migration is
isolated from a Kotlin upgrade; bump Kotlin as a separate, later change with its own PR. This also
defers the dokka problem, since dokka 0.9.17 only breaks under Kotlin 2.

## Stage 1 — Port existing features to 5.0.1

Each step should compile before starting the next.

- [x] **POM** — `apollo.version` → `5.0.1`; groupId `com.apollographql.apollo3` → `com.apollographql.apollo`; `java.version` → 17; `apollo-ast` → `apollo-ast-jvm` (matching what apollo-compiler's own POM depends on). Kotlin left at 1.9.21 and dokka untouched per the Stage 0 finding. **Verified: all dependencies resolve, zero resolution errors — every remaining failure is source-level.**
  - [ ] Still open, deferred to the cleanup step: drop `-Xuse-ir`; drop moshi if apollo-ast's JSON reader covers `SchemaDownloader`; check whether `apollo-api-jvm` is still needed by the plugin module at all.
- [ ] **`util/ConfigUtils.kt`, `util/SchemaDownloader.kt`** — introspection imports → `com.apollographql.apollo.ast.introspection`. Small, isolated, unblocks the rest.
- [ ] **`config/CompilerParams.kt`, `config/CompilationUnit.kt`** — clean break: delete `generateTestBuilders`, `generateResponseFields`, `operationIdGeneratorClass`, `metadataFiles`, `testDirectory`, `debugDirectory`. Remap survivors onto the three new options classes.
- [ ] **`GraphQLClientMojo.execute()`** — the real work. Build `InputFile` lists → three options objects → `buildSchemaAndOperationsSources(...)` → `SourceOutput.writeTo(...)`.
- [ ] **`apollo-client-maven-plugin-tests`** — bump `apollo-runtime`, fix package renames in `Query.kt`, `ApolloClientMavenPluginTest.kt`, `OperationManifestTest.kt`.
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
