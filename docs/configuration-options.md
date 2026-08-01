# Configuration options

Status of every plugin option against **Apollo Kotlin 5.0.1**.

Three sections: what the plugin **supports**, what was **removed** in the migration and why, and
what Apollo 5 offers that the plugin **does not expose yet**.

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

Available in Apollo 5 but not exposed by the plugin. Nothing here is blocked — each is additive
work. Roughly ordered by likely usefulness.

### Codegen

| Apollo option | What it does |
|---|---|
| `generateDataBuilders` | Type-safe data builders, the replacement for test builders. Needs a second `buildDataBuilders` call, not just a flag |
| `generateMethods` | Choose among `equalsHashCode`, `toString`, `copy`, `dataClass` |
| `addUnknownForEnums` | Whether enums get an `UNKNOWN` member |
| `addDefaultArgumentForInputObjects` | Default arguments on generated input objects |
| `generateInputBuilders` | Builders for input types *(experimental)* |
| `addJvmOverloads` | `@JvmOverloads` on generated constructors |
| `requiresOptInAnnotation` | Annotation to use for `@requiresOptIn` schema elements |
| `decapitalizeFields` | Lowercase leading field characters |
| `rootPackageName` | Derive package names from file paths under a root |
| `generatedSchemaName` | Rename the generated `__Schema` class |
| `classesForEnumsMatching` | Java equivalent of `sealedClassesForEnumsMatching` |
| `generatePrimitiveTypes` | Java primitives instead of boxed types |
| `generateApolloEnums`, `jsExport` | Experimental |

### Compiler behaviour

| Apollo feature | What it does |
|---|---|
| `ApolloCompilerPlugin` | The v5 extension point. Replaces the removed operation-ID generators and enables custom layouts and transforms. **The most significant gap** — it is the supported way to customise codegen in v5 |
| Multi-module via `CodegenMetadata` | Share fragments and types across modules |
| `operationIdsGenerator` | Custom operation IDs. Currently the Apollo default (SHA-256) |
| `addTypename` | Control `__typename` insertion. Currently the Apollo default (`ifFragments`) |
| `issueSeverities` (full map) | Only the deprecation severity is exposed, via `warnOnDeprecatedUsages` |
| `allowFragmentArguments` | Fragment arguments *(experimental)* |
| Foreign schemas / `@link` | Custom foreign schema definitions |
| Document and output transforms | `documentTransform`, `schemaDocumentTransform`, `javaOutputTransform`, `kotlinOutputTransform` |
| `layoutFactory` | Custom naming and file layout |
