# apollo-client-maven-plugin

A Maven plugin that runs the Apollo Kotlin compiler to generate GraphQL client code. Apollo ships an
official *Gradle* plugin only; this fills the gap for Maven builds.

Fork of `aoudiamoncef/apollo-client-maven-plugin`. Currently targets **Apollo Kotlin 5.0.1**.

## Build

```bash
./mvnw verify                          # full build and tests
./mvnw -pl apollo-client-maven-plugin -am compile   # plugin module only, fastest feedback
./mvnw test -Dgolden.update=true       # accept intentional codegen output changes
```

Requires **JDK 17+**. CI runs 17 and 21. Always use the wrapper, never a system `mvn`.

## Layout

| Module | Purpose |
|---|---|
| `apollo-client-maven-plugin` | The plugin. One mojo, `generate`, bound to `generate-sources` |
| `apollo-client-maven-plugin-tests` | Runs the plugin against a books schema, then executes the generated client against a live Undertow GraphQL server |

Inside the plugin module:

- `GraphQLClientMojo.kt` — the entry point, and the only place that calls Apollo's compiler
- `config/` — POJOs bound to the Maven XML configuration
- `util/` — schema download (OkHttp, independent of Apollo), config defaulting, file scanning

## The one thing to understand

**The entire Apollo surface this plugin depends on is three files.** Before changing anything that
touches codegen, read them in the Apollo source at the tag matching `apollo.version`:

- `libraries/apollo-compiler/src/main/kotlin/com/apollographql/apollo/compiler/ApolloCompiler.kt`
- `libraries/apollo-compiler/src/main/kotlin/com/apollographql/apollo/compiler/Options.kt`
- `libraries/apollo-compiler/src/main/kotlin/com/apollographql/apollo/compiler/codegen/SourceOutput.kt`

To get them:

```bash
git clone --depth 1 --branch v5.0.1 https://github.com/apollographql/apollo-kotlin
```

Match the tag to `apollo.version` in the root `pom.xml`. Reading a different version's sources than
the one being compiled against is a reliable way to waste an afternoon.

The call shape is: build `InputFile` lists → `CodegenSchemaOptions` + `IrOptions` + `CodegenOptions`
→ `ApolloCompiler.buildSchemaAndOperationsSources(...)` → `SourceOutput.writeTo(...)`.

## Gotchas that have already bitten

- **`CodegenOptions.validate()` throws if an option belonging to the *other* target language is set at
  all.** Java-only and Kotlin-only options must be passed as `null`, not `false`, when they do not
  apply. `GraphQLClientMojo` does this via an `isJava` check — preserve that pattern.
- **`dependency:tree` cannot tell you which Kotlin version you need.** Apollo declares an old
  `kotlin-stdlib` floor but its jars carry newer *binary metadata*. Only a compile reveals the
  mismatch. A "Module was compiled with an incompatible version of Kotlin" error means raise
  `kotlin.version`.
- **ktlint has `includeSources=false` in the tests module.** The only production source root there is
  the directory Apollo generates into. Without this, ktlint reformats generated code and the golden
  snapshot becomes a function of the ktlint version rather than of Apollo output.
- **`dokka` 0.9.17 does not work under Kotlin 2.** It is dormant because it only runs in the
  `publication` profile (`-Drelease`), which is unused while publishing is off. Fix it before
  revisiting publishing.

## Tests

Four tests, and they check different things — keep all of them:

- `ApolloClientMavenPluginTest` — generated client executes real queries against a live server
- `OperationManifestTest` — the operation manifest file is produced
- `GeneratedSourcesGoldenTest` — the *content* of generated sources is unchanged

The golden test is what catches an Apollo upgrade silently changing output that still happens to
compile. A diff there is not automatically a bug; review it, then accept with `-Dgolden.update=true`.

## Conventions

- Conventional commits, enforced by `.githooks/commit-msg` (`build:`, `ci:`, `docs:`, `feat:`, `fix:`,
  `test:`, …)
- ktlint formats on every build via the `format` profile; do not hand-format
- Not published anywhere. Consume via `./mvnw install`

## Further reading

- [docs/apollo-5-migration-plan.md](docs/apollo-5-migration-plan.md) — how the v3 → v5 migration went, the decisions behind it, and what remains
- [docs/configuration-options.md](docs/configuration-options.md) — every option: supported, removed, or not yet supported
