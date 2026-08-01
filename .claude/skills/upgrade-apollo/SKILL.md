---
name: upgrade-apollo
description: Upgrade this plugin to a new Apollo Kotlin version. Use when bumping apollo.version, when a Renovate Apollo PR is red, or when asked to move to a newer Apollo release.
---

# Upgrading Apollo Kotlin

This procedure is derived from the actual 3.8.4 → 5.0.1 migration. The failure modes listed here are
ones that really occurred, in the order they surfaced.

## First, size the job

**Patch or minor bump** (5.0.1 → 5.0.2, 5.0 → 5.1): usually just the version property. Follow the
steps; most will be no-ops.

**Major bump** (5 → 6): expect the compiler API to be *rewritten, not renamed*. Apollo did this
between 3 and 4. Budget real work and read the migration guide first:
`https://www.apollographql.com/docs/kotlin/migration/<version>`.

Do not promise a quick upgrade before checking which of these it is.

## Steps

### 1. Get the matching sources

```bash
git clone --depth 1 --branch v<new-version> https://github.com/apollographql/apollo-kotlin
```

The tag must match the version being compiled against. Reading a neighbouring version's sources
produces confident, wrong conclusions.

### 2. Bump the version

`apollo.version` in the root `pom.xml`. Nothing else yet — change one variable at a time so a failure
has one obvious cause.

### 3. Compile the plugin module

```bash
./mvnw -pl apollo-client-maven-plugin -am clean compile
```

`clean` matters: the incremental compiler will report success having compiled nothing if only
dependencies changed.

Filter to errors; the raw output is long.

### 4. Work the error list

Expect these categories, in roughly this order.

**"Module was compiled with an incompatible version of Kotlin"** — raise `kotlin.version` to at least
the metadata version named in the error, then recompile. Note this is invisible to `dependency:tree`,
which shows only the declared stdlib floor. Check the *whole* error list before choosing a version;
transitive dependencies such as okio have set the floor higher than Apollo's own artifacts did.

**Unresolved references** — diff the compiler API against the previous tag:

```bash
git -C apollo-kotlin diff v<old> v<new> -- libraries/apollo-compiler/src/main/kotlin/com/apollographql/apollo/compiler/Options.kt
git -C apollo-kotlin diff v<old> v<new> -- libraries/apollo-compiler/src/main/kotlin/com/apollographql/apollo/compiler/ApolloCompiler.kt
```

That diff is the authoritative answer to "what happened to X", and is far faster than searching docs.

**An option disappeared entirely** — check whether it moved to `ApolloCompilerPlugin` (Apollo's
extension point), and follow the clean-break convention: remove it, do not silently ignore it. Record
it in `docs/configuration-options.md` under *Removed*, with the reason and the replacement.

### 5. Full build

```bash
./mvnw verify
```

The tests module exercises the plugin for real, so this is where runtime problems surface —
`CodegenOptions.validate()` failures in particular, since those are runtime errors rather than
compile errors.

### 6. Handle the golden diff

A `GeneratedSourcesGoldenTest` failure means generated *output* changed. This is expected on many
upgrades and is not automatically a bug.

Read the diff. Decide whether the change is benign (formatting, ordering, new annotations) or
meaningful (changed nullability, renamed classes, different adapters). Meaningful changes are
breaking for downstream users and belong in the summary.

Accept with:

```bash
./mvnw test -Dgolden.update=true
```

Never accept a golden diff without reading it. Doing so defeats the test's only purpose.

### 7. Update the documentation

- `docs/configuration-options.md` — move options between the supported / removed / not-yet-supported
  sections as needed
- `CLAUDE.md` — the version in the header and the clone tag
- `docs/apollo-5-migration-plan.md` — only for a major bump, where the narrative is worth keeping

### 8. Commit

Conventional commits, enforced by a hook. Separate mechanical changes (version bumps) from semantic
ones (API migration) so a reviewer can follow them independently.

## Reporting back

State plainly:

- which Apollo version, and whether the compiler API changed
- whether generated output changed, and if so, whether it is breaking for downstream users
- anything removed, and what replaces it
- anything deferred, and why

If the upgrade is partly blocked, finish everything that is not blocked, then say exactly what was
left and why. Do not quietly narrow the scope.
