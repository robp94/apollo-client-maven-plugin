package com.github.aoudiamoncef.apollo.plugin

import com.apollographql.apollo.ast.DeprecatedUsage
import com.apollographql.apollo.compiler.ApolloCompiler
import com.apollographql.apollo.compiler.CodegenSchemaOptions
import com.apollographql.apollo.compiler.IssueSeverity
import com.apollographql.apollo.compiler.TargetLanguage
import com.apollographql.apollo.compiler.buildCodegenOptions
import com.apollographql.apollo.compiler.buildIrOptions
import com.apollographql.apollo.compiler.codegen.writeTo
import com.apollographql.apollo.compiler.toInputFiles
import com.github.aoudiamoncef.apollo.plugin.config.CompilationUnit
import com.github.aoudiamoncef.apollo.plugin.config.CompilerParams
import com.github.aoudiamoncef.apollo.plugin.config.Introspection
import com.github.aoudiamoncef.apollo.plugin.config.Service
import com.github.aoudiamoncef.apollo.plugin.util.ConfigUtils
import com.github.aoudiamoncef.apollo.plugin.util.SchemaDownloader
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.logging.Log
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.apache.maven.project.MavenProject
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

/**
 * Generate queries classes for a GraphQl API
 */
@Mojo(
    name = "generate",
    requiresDependencyCollection = ResolutionScope.COMPILE,
    requiresDependencyResolution = ResolutionScope.COMPILE,
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    threadSafe = true,
)
class GraphQLClientMojo : AbstractMojo() {
    /**
     * Maven project instance
     */
    @Parameter(defaultValue = "\${project}", readonly = true, required = true)
    private lateinit var project: MavenProject

    /**
     * Whether to skip plugin execution
     */
    @Parameter
    private val skip: Boolean = false

    /**
     * registers services
     *
     * name: the name of the [Service], must be unique
     */
    @Parameter
    private lateinit var services: Map<String, Service>

    @Throws(MojoExecutionException::class)
    override fun execute() {
        val start = System.nanoTime()
        if (skip) {
            log.info("Apollo GraphQL Client code generation skipping execution because skip option is true")
            return
        }

        if (!this::services.isInitialized) {
            log.error("Apollo GraphQL Client code generation failed because of wrong settings")
        }

        log.info("Apollo GraphQL Client code generation task started")
        services.entries.forEach service@{
            if (!it.value.enabled) {
                log.info("Skipping generation of service: ${it.key} because enabled option is false")
                return@service
            }

            val service: Service = ConfigUtils.checkService(project, it.key, it.value)
            val compilationUnit: CompilationUnit =
                ConfigUtils.checkCompilationUnit(project, it.key, service.compilationUnit)
            val compilerParams = ConfigUtils.checkCompilerParams(project, service, compilationUnit.compilerParams)
            val introspection: Introspection = ConfigUtils.checkIntrospection(project, service)

            log.info("Generating service: ${it.key}")

            if (introspection.enabled) {
                log.info("Automatically generating introspection file from: ${introspection.endpointUrl}")
                introspection.schemaFile.let { schema ->
                    val okHttpClient =
                        SchemaDownloader.newOkHttpClient(
                            connectTimeoutSeconds = introspection.connectTimeoutSeconds,
                            readTimeoutSeconds = introspection.readTimeoutSeconds,
                            writeTimeoutSeconds = introspection.writeTimeoutSeconds,
                            useSelfSignedCertificat = introspection.useSelfSignedCertificat,
                            useGzip = introspection.useGzip,
                        )
                    if (introspection.endpointUrl.isNotEmpty()) {
                        SchemaDownloader.downloadIntrospection(
                            schema = schema as File,
                            endpoint = introspection.endpointUrl,
                            headers = introspection.headers,
                            prettyPrint = introspection.prettyPrint,
                            okHttpClient = okHttpClient,
                        )
                    } else if (introspection.graph.isNotEmpty()) {
                        SchemaDownloader.downloadRegistry(
                            graph = introspection.graph,
                            schema = schema as File,
                            key = introspection.key,
                            variant = introspection.graphVariant,
                            prettyPrint = introspection.prettyPrint,
                            okHttpClient = okHttpClient,
                        )
                    }
                }
            }

            log.info("Read schema file")
            val sourceSetFiles =
                ConfigUtils.getSourceSetFiles(
                    sourceFolder = service.sourceFolder as File,
                    includes = service.includes,
                    excludes = service.excludes,
                )
            val schemaMatcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{json,sdl,graphqls}")
            val directories = ConfigUtils.findFilesByMatcher(sourceSetFiles, schemaMatcher)
            val resolveSchema =
                ConfigUtils.resolveSchema(
                    project = project,
                    schemaPath = service.schemaPath,
                    directories = directories,
                    sourceSetFiles = sourceSetFiles,
                )

            log.info("Read querie(s)/fragment(s) files")
            val graphqlMatcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.{graphql,gql,graphqls}")
            val graphqlFiles =
                ConfigUtils
                    .findFilesByMatcher(sourceSetFiles, graphqlMatcher)
                    .takeIf { set -> set.isNotEmpty() }
                    ?: throw MojoExecutionException("No querie(s)/fragment(s) found")

            val isJava = compilerParams.targetLanguage == TargetLanguage.JAVA

            val codegenSchemaOptions =
                CodegenSchemaOptions(
                    scalarTypeMapping = compilerParams.scalarsMapping.mapValues { it.value.targetName },
                    scalarAdapterMapping =
                        compilerParams.scalarsMapping
                            .filterValues { mapping -> mapping.expression != null }
                            .mapValues { it.value.expression!! },
                    generateDataBuilders = false,
                )

            val irOptions =
                buildIrOptions(
                    alwaysGenerateTypesMatching = compilerParams.alwaysGenerateTypesMatching,
                    codegenModels = compilerParams.codegenModels.label,
                    flattenModels = compilerParams.flattenModels,
                    failOnWarnings = compilerParams.failOnWarnings,
                    generateOptionalOperationVariables = compilerParams.generateOptionalOperationVariables,
                    addTypename = compilerParams.addTypename,
                    allowFragmentArguments = compilerParams.allowFragmentArguments,
                    issueSeverity = resolveIssueSeverities(compilerParams),
                )

            // `CodegenOptions.validate()` throws if an option belonging to the other target language
            // is set at all, so these must be null rather than false when they do not apply.
            val codegenOptions =
                buildCodegenOptions(
                    targetLanguage = compilerParams.targetLanguage,
                    packageName = compilerParams.packageName,
                    useSemanticNaming = compilerParams.useSemanticNaming,
                    operationManifestFormat = compilerParams.operationManifestFormat,
                    generateFragmentImplementations = compilerParams.generateFragmentImplementations,
                    generateQueryDocument = compilerParams.generateQueryDocument,
                    generateSchema = compilerParams.generateSchema,
                    generateMethods = compilerParams.generateMethods,
                    addUnknownForEnums = compilerParams.addUnknownForEnums,
                    addDefaultArgumentForInputObjects = compilerParams.addDefaultArgumentForInputObjects,
                    generatedSchemaName = compilerParams.generatedSchemaName,
                    generateModelBuilders = if (isJava) compilerParams.generateModelBuilders else null,
                    nullableFieldStyle = if (isJava) compilerParams.nullableFieldStyle else null,
                    generatePrimitiveTypes = if (isJava) compilerParams.generatePrimitiveTypes else null,
                    classesForEnumsMatching = if (isJava) compilerParams.classesForEnumsMatching else null,
                    generateAsInternal = if (isJava) null else compilerParams.generateAsInternal,
                    generateFilterNotNull = if (isJava) null else compilerParams.generateFilterNotNull,
                    sealedClassesForEnumsMatching = if (isJava) null else compilerParams.sealedClassesForEnumsMatching,
                    addJvmOverloads = if (isJava) null else compilerParams.addJvmOverloads,
                    generateInputBuilders = if (isJava) null else compilerParams.generateInputBuilders,
                    requiresOptInAnnotation = if (isJava) null else compilerParams.requiresOptInAnnotation,
                )

            val sourceOutput =
                ApolloCompiler.buildSchemaAndOperationsSources(
                    schemaFiles = listOf(resolveSchema!!).toInputFiles(),
                    executableFiles = graphqlFiles.toInputFiles(),
                    codegenSchemaOptions = codegenSchemaOptions,
                    irOptions = irOptions,
                    codegenOptions = codegenOptions,
                    layoutFactory = null,
                    operationIdsGenerator = null,
                    irOperationsTransform = null,
                    javaOutputTransform = null,
                    kotlinOutputTransform = null,
                    documentTransform = null,
                    schemaDocumentTransform = null,
                    logger = MavenApolloLogger(log),
                    operationManifestFile = compilationUnit.operationOutputFile,
                )

            sourceOutput.writeTo(compilationUnit.outputDirectory as File, true, null)

            if (service.addSourceRoot) {
                val generatedSourcePath = compilationUnit.outputDirectory?.canonicalPath
                log.info("Add the compiled sources from $generatedSourcePath to project root")
                project.addCompileSourceRoot(generatedSourcePath)
            }

            if (service.addTestSourceRoot) {
                val generatedSourcePath = compilationUnit.outputDirectory?.canonicalPath
                log.info("Add the test compiled sources from $generatedSourcePath to project root")
                project.addTestCompileSourceRoot(generatedSourcePath)
            }
        }
        log.info("Apollo GraphQL Client code generation task finished")

        val finish = System.nanoTime()
        val timeElapsed = (finish - start).toDouble() / 1000000000
        log.info("Total time: ${String.format("%.3f", timeElapsed)} s")
    }

    /**
     * Combines [CompilerParams.warnOnDeprecatedUsages] with the finer-grained
     * [CompilerParams.issueSeverities] map.
     *
     * `warnOnDeprecatedUsages` no longer exists as an Apollo flag; deprecation is just one issue type
     * whose severity is configurable. Warn is already Apollo's default, so the boolean only needs to
     * say anything when the user opted out. An explicit `issueSeverities` entry wins, so that the
     * more specific setting is not silently overridden by the coarser one.
     *
     * Returns null when nothing was configured, leaving Apollo's defaults untouched.
     */
    private fun resolveIssueSeverities(compilerParams: CompilerParams): Map<String, IssueSeverity>? {
        val severities = mutableMapOf<String, IssueSeverity>()

        if (!compilerParams.warnOnDeprecatedUsages) {
            severities[DeprecatedUsage::class.simpleName!!] = IssueSeverity.Ignore
        }

        compilerParams.issueSeverities.forEach { (issue, severity) ->
            severities[issue] = parseIssueSeverity(issue, severity)
        }

        return severities.takeIf { it.isNotEmpty() }
    }

    private fun parseIssueSeverity(
        issue: String,
        value: String,
    ): IssueSeverity =
        IssueSeverity.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw MojoExecutionException(
                "Unknown issue severity '$value' for issue '$issue'. Expected one of: " +
                    IssueSeverity.entries.joinToString { it.name },
            )

    /**
     * Bridges the Apollo compiler's diagnostics onto the Maven log.
     *
     * Apollo 5 made its own `NoOpLogger` private, so a logger has to be supplied. Routing it to the
     * Maven log means schema warnings and deprecation notices surface in the build output instead of
     * being discarded, which is what the previous no-op logger did.
     */
    private class MavenApolloLogger(
        private val log: Log,
    ) : ApolloCompiler.Logger {
        override fun debug(message: String) = log.debug(message)

        override fun info(message: String) = log.info(message)

        override fun warning(message: String) = log.warn(message)

        override fun error(message: String) = log.error(message)
    }
}
