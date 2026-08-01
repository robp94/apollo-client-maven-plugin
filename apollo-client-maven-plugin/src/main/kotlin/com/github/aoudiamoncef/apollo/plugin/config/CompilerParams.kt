package com.github.aoudiamoncef.apollo.plugin.config

import com.apollographql.apollo.compiler.JavaNullable
import com.apollographql.apollo.compiler.MANIFEST_NONE
import com.apollographql.apollo.compiler.TargetLanguage

/**
 * CompilerParams contains all the parameters needed to invoke the apollo compiler.
 *
 * Fields are grouped by which Apollo options object they feed, since the compiler splits what used
 * to be one flat `Options` into [com.apollographql.apollo.compiler.CodegenSchemaOptions],
 * [com.apollographql.apollo.compiler.IrOptions] and
 * [com.apollographql.apollo.compiler.CodegenOptions].
 */
class CompilerParams {

    /**
     * Warn if using a deprecated field
     *
     * Default value: true
     */
    internal val warnOnDeprecatedUsages: Boolean = true

    /**
     * Fail the build if there are warnings. This is not named `allWarningAsErrors` to avoid nameclashes with the Kotlin options
     *
     * Default value: false
     */
    internal val failOnWarnings: Boolean = false

    /**
     * For custom scalar types like Date, map from the GraphQL type to the java/kotlin type.
     *
     * Default value: the empty map
     */
    var scalarsMapping: Map<String, ScalarMapping> = emptyMap()

    /**
     * When true, the generated classes names will end with 'Query' or 'Mutation'.
     * If you write `query droid { ... }`, the generated class will be named 'DroidQuery'.
     *
     * Default value: true
     */
    internal val useSemanticNaming: Boolean = true

    /**
     * Whether to generate Kotlin models with `internal` visibility modifier.
     *
     * Default value: false
     */
    internal val generateAsInternal: Boolean = false

    /**
     * A list of [Regex] patterns for GraphQL enums that should be generated as Kotlin sealed classes instead of the default Kotlin enums.
     *
     * Use this if you want your client to have access to the rawValue of the enum. This can be useful if new GraphQL enums are added but
     * the client was compiled against an older schema that doesn't have knowledge of the new enums.
     *
     * Default: emptyList()
     */
    internal val sealedClassesForEnumsMatching: List<String> = emptyList()

    /**
     * The format in which the operation manifest will be generated.
     *
     * Acceptable values:
     * - `none`: No manifest will be generated
     * - `operationOutput`: 'operationOutput' manifest format
     * - `persistedQueryManifest`: Apollo's persistent query manifest format
     *
     * Default value: "none"
     */
    internal val operationManifestFormat: String = MANIFEST_NONE

    /**
     * A list of [Regex] patterns for input/scalar/enum types that should be generated whether or not they are used by
     * queries/fragments in this module.
     *
     * Default value: the empty set
     */
    internal var alwaysGenerateTypesMatching: Set<String> = setOf()

    /**
     * The package name of the models. The compiler will generate classes in
     *
     * - $packageName/SomeQuery.kt
     * - $packageName/fragment/SomeFragment.kt
     * - $packageName/type/CustomScalar.kt
     * - $packageName/type/SomeInputObject.kt
     * - $packageName/type/SomeEnum.kt
     *
     * Default value: ""
     */
    internal var packageName: String? = ""

    /**
     * Whether to generate default implementation classes for GraphQL fragments.
     * Default value is `false`, means only interfaces are been generated.
     *
     * Most of the time, fragment implementations are not needed because you can easily access fragments interfaces and read all
     * data from your queries. They are needed if you want to be able to build fragments outside an operation. For an exemple
     * to programmatically build a fragment that is reused in another part of your code or to read and write fragments to the cache.
     */
    internal val generateFragmentImplementations: Boolean = false

    /**
     * Whether to embed the query document in the [com.apollographql.apollo.api.Operation]s. By default this is true as it is needed
     * to send the operations to the server.
     * If performance is critical and you have a way to whitelist/read the document from another place, disable this.
     */
    internal val generateQueryDocument: Boolean = true

    /**
     * Whether to generate the __Schema class. The __Schema class lists all composite
     * types in order to access __typename and/or possibleTypes
     */
    internal val generateSchema: Boolean = false

    /**
     * Whether to generate operation variables as [com.apollographql.apollo.api.Optional]
     *
     * Using [com.apollographql.apollo.api.Optional] allows to omit the variables if needed but makes the
     * callsite more verbose in most cases.
     *
     * Default: true
     */
    internal val generateOptionalOperationVariables: Boolean = true

    /**
     * Kotlin native will generate [Any?] for optional types
     * Setting generateFilterNotNull will generate extra `filterNotNull` functions that will help keep the type information
     */
    internal val generateFilterNotNull: Boolean = false

    /**
     * The language of the generated code.
     *
     * Either [TargetLanguage.JAVA] or [TargetLanguage.KOTLIN_1_9].
     *
     * Default: [TargetLanguage.JAVA].
     */
    internal val targetLanguage: TargetLanguage = TargetLanguage.JAVA

    /**
     * Whether to generate builders for java models
     *
     * Default value: false
     * Only valid when [targetLanguage] is [TargetLanguage.JAVA]
     */
    internal val generateModelBuilders: Boolean = false

    /**
     * The style to use for fields that are nullable in the Java generated code.
     *
     * Only valid when [targetLanguage] is [TargetLanguage.JAVA]
     *
     * Acceptable values:
     * - `none`: Fields will be generated with the same type whether they are nullable or not
     * - `apolloOptional`: Fields will be generated as Apollo's `com.apollographql.apollo.api.Optional<Type>` if nullable, or `Type` if not.
     * - `javaOptional`: Fields will be generated as Java's `java.util.Optional<Type>` if nullable, or `Type` if not.
     * - `guavaOptional`: Fields will be generated as Guava's `com.google.common.base.Optional<Type>` if nullable, or `Type` if not.
     * - `jetbrainsAnnotations`: Fields will be generated with Jetbrain's `org.jetbrains.annotations.Nullable` annotation if nullable, or
     * `org.jetbrains.annotations.NotNull` if not.
     * - `androidAnnotations`: Fields will be generated with Android's `androidx.annotation.Nullable` annotation if nullable, or
     * `androidx.annotation.NonNull` if not.
     * - `jsr305Annotations`: Fields will be generated with JSR 305's `javax.annotation.Nullable` annotation if nullable, or
     * `javax.annotation.Nonnull` if not.
     *
     * Default: `none`
     */
    internal val nullableFieldStyle: JavaNullable = JavaNullable.NONE

    /**
     * What codegen to use. One of [Codegen.OPERATION], [Codegen.RESPONSE] or
     * [Codegen.OPERATION_WITH_INTERFACES].
     *
     * Default value: [Codegen.OPERATION]
     */
    internal val codegenModels: Codegen = Codegen.OPERATION

    /**
     * Whether to flatten the models. File paths are limited on MacOSX to 256 chars and flattening can help keeping the path length manageable
     * The drawback is that some classes may nameclash in which case they will be suffixed with a number
     *
     * Default value: true for "operationBased" and "responseBased", false else
     */
    internal val flattenModels: Boolean = true
}
