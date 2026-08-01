# Configuration options

Status of every plugin option against **Apollo Kotlin 5.0.1**.

Three sections: what the plugin **supports**, what was **removed** in the migration and why, and
what Apollo 5 offers that the plugin **does not expose yet**.

The *not yet supported* section is prioritised for a **JVM consumer** — a Java or Kotlin service
calling a GraphQL API. Kotlin Multiplatform, Native and JS options are called out separately so they
are not mistaken for work worth doing.

Generated from the source as of 2026-08-01. See [apollo-5-migration-plan.md](apollo-5-migration-plan.md)
for the migration itself.

---

## Supported

### `<service>`

The top level of the configuration. Each entry under `<services>` is keyed by service name.

| Option | Type | Default | Notes |
|---|---|---|---|
| `enabled` | boolean | `true` | Skip this service when false |
| `addSourceRoot` | boolean | `true` | Add the generated directory as a compile source root |
| `addTestSourceRoot` | boolean | `false` | Add the generated directory as a test compile source root |
| `sourceFolder` | file | `src/main/graphql/<name>` | Where the `.graphql` files live |
| `schemaPath` | string | *(auto-discovered)* | Falls back to searching for `schema.json`, `schema.sdl` or `schema.graphqls` |
| `includes` | set | `**/*.graphql`, `**/*.gql`, `**/*.json`, `**/*.sdl` | |
| `excludes` | set | *(empty)* | |
| `compilationUnit` | element | | See below |
| `introspection` | element | | See below |

### `<compilationUnit>`

| Option | Type | Default | Notes |
|---|---|---|---|
| `name` | string | *(service name)* | |
| `outputDirectory` | file | `target/generated-sources/apollo/<name>` | Deleted and rewritten on each run |
| `generateOperationDescriptors` | boolean | `false` | Creates the operation manifest file |
| `operationOutputFile` | file | `target/generated/operationOutput/apollo/<name>/operationOutput.json` | Only written when `generateOperationDescriptors` is true |
| `compilerParams` | element | | See below |

### `<compilerParams>`

The Apollo column shows which of the three v5 options objects each value feeds.

| Option | Type | Default | Apollo 5 target |
|---|---|---|---|
| `packageName` | string | `<groupId>.apollo.client.<name>` | `CodegenOptions.packageName` |
| `targetLanguage` | `JAVA` \| `KOTLIN_1_9` | `JAVA` | `CodegenOptions.targetLanguage` |
| `codegenModels` | `OPERATION` \| `RESPONSE` \| `OPERATION_WITH_INTERFACES` | `OPERATION` | `IrOptions.codegenModels` |
| `scalarsMapping` | map of `targetName` + optional `expression` | *(empty)* | `CodegenSchemaOptions.scalarTypeMapping` and `scalarAdapterMapping` |
| `operationManifestFormat` | `none` \| `persistedQueryManifest` | `none` | `CodegenOptions.operationManifestFormat` |
| `alwaysGenerateTypesMatching` | set of regex | *(empty)* | `IrOptions.alwaysGenerateTypesMatching` |
| `flattenModels` | boolean | `true` | `IrOptions.flattenModels` |
| `useSemanticNaming` | boolean | `true` | `CodegenOptions.useSemanticNaming` |
| `warnOnDeprecatedUsages` | boolean | `true` | `IrOptions.issueSeverities` |
| `failOnWarnings` | boolean | `false` | `IrOptions.failOnWarnings` |
| `generateOptionalOperationVariables` | boolean | `true` | `IrOptions.generateOptionalOperationVariables` |
| `generateFragmentImplementations` | boolean | `false` | `CodegenOptions` |
| `generateQueryDocument` | boolean | `true` | `CodegenOptions` |
| `generateSchema` | boolean | `false` | `CodegenOptions` |
| `generateAsInternal` | boolean | `false` | `CodegenOptions` — **Kotlin only** |
| `generateFilterNotNull` | boolean | `false` | `CodegenOptions` — **Kotlin only** |
| `sealedClassesForEnumsMatching` | list of regex | *(empty)* | `CodegenOptions` — **Kotlin only** |
| `generateModelBuilders` | boolean | `false` | `CodegenOptions` — **Java only** |
| `nullableFieldStyle` | `JavaNullable` | `NONE` | `CodegenOptions` — **Java only** |

> **Language-specific options.** Apollo's `CodegenOptions.validate()` throws if an option belonging to
> the *other* target language is set at all. The plugin therefore passes Java-only options only when
> `targetLanguage` is `JAVA`, and Kotlin-only options only when it is not. Setting one that does not
> apply to your target is ignored rather than fatal, but it is misleading — leave it out.

### `<introspection>`

Schema downloading. Unchanged by the Apollo 5 migration; the plugin does this itself with OkHttp
rather than through Apollo.

| Option | Type | Default |
|---|---|---|
| `enabled` | boolean | `false` |
| `endpointUrl` | string | *(empty — required when enabled)* |
| `headers` | map | *(empty)* |
| `schemaFile` | file | `src/main/graphql/<name>/schema.json` |
| `prettyPrint` | boolean | `false` |
| `graph` | string | *(empty)* |
| `graphVariant` | string | *(empty)* |
| `key` | string | *(empty)* |
| `connectTimeoutSeconds` | long | `10` |
| `readTimeoutSeconds` | long | `10` |
| `writeTimeoutSeconds` | long | `10` |
| `useSelfSignedCertificat` | boolean | `false` |
| `useGzip` | boolean | `false` |

Use `endpointUrl` for a plain introspection endpoint, or `graph` + `graphVariant` + `key` to pull
from the Apollo registry.

---

## Removed

These were deleted outright rather than deprecated. **A POM still setting one will fail** with an
unknown-parameter error, which is deliberate: a silent no-op would be worse than a build failure.

| Option | Why | What to do instead |
|---|---|---|
| `generateKotlinModels` | Superseded before v5; the language is one setting, not two | Set `targetLanguage` to `KOTLIN_1_9` |
| `schemaPackageName` | v5 has a single package name; schema types go to `<packageName>/type/` | Use `packageName` |
| `rootFolders` | Only existed to derive package names from directory layout | Use `packageName` |
| `operationIdGeneratorClass` | Apollo removed `OperationIdGenerator` and `OperationOutputGenerator` in v5 | No replacement exposed — see *Not yet supported* |
| `generateResponseFields` | Removed in Apollo 4 | — |
| `generateTestBuilders` | Removed in Apollo 4; test builders were replaced by data builders | See *Not yet supported* |
| `generateApolloMetadata`, `metadataFiles`, `metadataOutputFile` | Multi-module codegen was redesigned around `CodegenMetadata` | See *Not yet supported* |
| `testDirectory`, `debugDirectory` | v5 emits a single source tree | Use `outputDirectory` |
| `logger` | The compiler log is now wired to the Maven log automatically | — |
| `codegenModels=COMPATIBILITY` | The `compat` model was removed in Apollo 4 | Use `OPERATION` |
| `targetLanguage=KOTLIN_1_5` | A `DeprecationLevel.ERROR` symbol in v5 | Use `KOTLIN_1_9` |

---

## Not yet supported

Available in Apollo 5 but not exposed by the plugin.

**Prioritised for a JVM consumer** — a Java or Kotlin service (Quarkus, Spring Boot) calling a
GraphQL API. Kotlin Multiplatform, JS and Native concerns are deliberately at the bottom. Nothing
here is blocked; the split below is by *effort*, not by feasibility.

### Tier 1 — pass-through

Each of these is one field on `CompilerParams` plus one argument in `GraphQLClientMojo`. Apollo
already accepts them on `buildCodegenOptions` / `buildIrOptions`; the plugin simply does not pass
them. Adding several at once is barely more work than adding one.

| Apollo option | Language | What it does | Why you would want it |
|---|---|---|---|
| `generateMethods` | both | Pick among `equalsHashCode`, `toString`, `copy`, `dataClass` | The most broadly useful gap. Java defaults to `equalsHashCode` + `toString`; Kotlin to `dataClass`. Controls whether models are usable as map keys, in assertions, and in logs |
| `addUnknownForEnums` | both | Whether generated enums carry an `UNKNOWN` member | Schema evolution. Without it, a value the server adds after your build can blow up deserialisation |
| `addDefaultArgumentForInputObjects` | both | Default arguments on generated input objects | Much less verbose construction of input types with many optional fields |
| `generatePrimitiveTypes` | Java | `int`/`double`/`boolean` instead of boxed types | Avoids needless boxing and accidental `null` unboxing in Java models |
| `classesForEnumsMatching` | Java | Java equivalent of `sealedClassesForEnumsMatching` | Access to the raw enum value for unknown server-side values |
| `addJvmOverloads` | Kotlin | `@JvmOverloads` on generated constructors | Matters when Java code calls Kotlin-generated classes — a mixed Java/Kotlin codebase |
| `generateInputBuilders` | Kotlin | Builders for input types *(experimental)* | Constructors require wrapping every optional field in `Optional`; builders avoid that |
| `requiresOptInAnnotation` | Kotlin | Annotation used for `@requiresOptIn` schema elements | Only if your schema marks elements as requiring opt-in |
| `generatedSchemaName` | both | Rename the generated `__Schema` class | Cosmetic; only relevant with `generateSchema` |
| `addTypename` | both | When `__typename` is injected | Default `ifFragments` is almost always right. Change only for an unusual server |
| `issueSeverities` (full map) | both | Severity per issue type | Today only deprecation is exposed, via `warnOnDeprecatedUsages`. The full map also covers unused fragments and variables |
| `allowFragmentArguments` | both | Fragment arguments *(experimental)* | Only if your operations use them |

> **Two implementation notes.** New Java-only or Kotlin-only options must follow the existing `isJava`
> pattern in the mojo and be passed as `null` when they do not apply, or `CodegenOptions.validate()`
> throws. And `decapitalizeFields` exists on **both** `IrOptions` and `CodegenOptions` — if it is ever
> exposed, it must be set consistently on both or codegen and IR will disagree.

### Tier 2 — needs a little machinery

Still small, but not pure plumbing.

| Apollo feature | What it does | Why it is more work |
|---|---|---|
| `generateDataBuilders` | Type-safe builders for constructing fake responses — the replacement for the removed test builders | The flag on `CodegenSchemaOptions` is not enough. Emitting them needs a separate `ApolloCompiler.buildDataBuilders(...)` call and its output written alongside the main sources. **Most valuable item in this tier** if you want to build fixtures for service tests without hand-writing JSON |
| `rootPackageName` | Derive package names from file paths, prefixed by a root | The parameter exists, but the plugin passes every file with an empty `normalizedPath` (via `toInputFiles()`), so setting it alone does nothing. Needs real relative-path computation first — this is what the removed `rootFolders` used to feed |

### Tier 3 — larger, and probably not needed here

| Apollo feature | What it does | Relevance |
|---|---|---|
| `ApolloCompilerPlugin` | The v5 extension point: custom layouts, transforms, operation IDs | Real work. Only worth it if you need to customise codegen itself |
| Custom operation IDs (`operationIdsGenerator`) | Replaces the default SHA-256 operation ID | Only for persisted queries **with a bespoke ID scheme**. Standard APQ expects the SHA-256 default, which is what the plugin already produces. The mojo already accepts this parameter and passes `null`, so restoring it is a config option plus a `Class.forName` |
| Multi-module via `CodegenMetadata` | Share fragments and types across modules | Only if you split codegen across several Maven modules |
| Document and output transforms | `documentTransform`, `schemaDocumentTransform`, `javaOutputTransform`, `kotlinOutputTransform` | Advanced codegen customisation |
| `layoutFactory` | Custom naming and file layout | Advanced |
| Foreign schemas / `@link` | Custom foreign schema definitions | Rare |

### Not relevant to a JVM service

Supported by Apollo, but meaningless outside Kotlin Multiplatform, Native or JS. Listed so nobody
spends time on them by mistake.

| Apollo option | Why not |
|---|---|
| `jsExport` | Kotlin/JS only |
| `generateApolloEnums` | Experimental, and aimed at multiplatform enum handling |
| `generateFilterNotNull` | *Already exposed*, but only does anything for Kotlin Native. Harmless to leave at `false` |
