package com.lahzouz.java.graphql.client.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Pins the *content* of the generated sources, not merely the fact that codegen ran.
 *
 * The other tests prove the generated code compiles and executes against a real server. They would
 * not notice an Apollo upgrade quietly changing what the compiler emits, as long as the result still
 * happened to compile. This test would.
 *
 * That is the point of it: after an Apollo version bump, a diff here is the signal that the upgrade
 * changed generated output, and the diff itself shows exactly how.
 *
 * To accept an intentional change:
 *
 *     ./mvnw test -Dgolden.update=true
 *
 * then review the resulting diff before committing it.
 */
class GeneratedSourcesGoldenTest {
    private val generatedRoot: Path = Paths.get("target/generated-sources/graphql-client/books")
    private val goldenRoot: Path = Paths.get("src/test/golden/books")

    @Test
    @DisplayName("generated sources match the checked-in golden files")
    fun generatedSourcesMatchGoldenFiles() {
        assertTrue(
            Files.isDirectory(generatedRoot),
            "No generated sources found at $generatedRoot. Did the plugin run?",
        )

        val generated = readTree(generatedRoot)
        assertTrue(generated.isNotEmpty(), "Codegen produced no files at all")

        if (System.getProperty("golden.update") == "true") {
            rewriteGoldenFiles(generated)
            return
        }

        assertTrue(
            Files.isDirectory(goldenRoot),
            "No golden files at $goldenRoot. Create them with -Dgolden.update=true",
        )

        val golden = readTree(goldenRoot)

        assertEquals(
            golden.keys.sorted(),
            generated.keys.sorted(),
            "The set of generated files changed. Re-run with -Dgolden.update=true to accept.",
        )

        generated.forEach { (relativePath, content) ->
            assertEquals(
                golden[relativePath],
                content,
                "Generated content changed for $relativePath. Re-run with -Dgolden.update=true to accept.",
            )
        }
    }

    /**
     * Line endings are normalised because the golden files round-trip through git on Windows, where
     * they come back as CRLF while the compiler emits LF.
     */
    private fun readTree(root: Path): Map<String, String> =
        root
            .toFile()
            .walkTopDown()
            .filter(File::isFile)
            .associate { file ->
                root.relativize(file.toPath()).joinToString("/") to file.readText().replace("\r\n", "\n")
            }

    private fun rewriteGoldenFiles(generated: Map<String, String>) {
        goldenRoot.toFile().deleteRecursively()
        generated.forEach { (relativePath, content) ->
            val target = goldenRoot.resolve(relativePath)
            Files.createDirectories(target.parent)
            target.toFile().writeText(content)
        }
    }
}
