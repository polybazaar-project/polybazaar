// Co-authored-by: Claude Code (Claude Opus 5.5)
package com.android.polybazaar

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps ARCHITECTURE.md in step with the code: a package or a Firestore collection added without
 * describing it there, or a type it names that was renamed or removed, fails the build, so agents
 * and teammates can trust the document.
 */
class ArchitectureDocTest {

  private val root: File =
      generateSequence(File("").absoluteFile) { it.parentFile }
          .firstOrNull { File(it, "settings.gradle.kts").isFile }
          ?: error("No settings.gradle.kts above ${File("").absolutePath}")

  private val architecture: String by lazy {
    val doc = File(root, "ARCHITECTURE.md")
    assertTrue("ARCHITECTURE.md is missing from the repo root", doc.isFile)
    doc.readText()
  }

  @Test
  fun architectureDoc_namesEveryPackage() {
    val packages =
        File(root, "app/src/main/java/com/android/polybazaar")
            .listFiles(File::isDirectory)
            .orEmpty()
            .map { "`${it.name}/`" }
    assertNamed(packages, "packages")
  }

  @Test
  fun architectureDoc_namesEveryFirestoreCollection() {
    val collections =
        File(root, "firestore.rules")
            .readLines()
            .filter { it.trim().startsWith("match ") }
            .flatMap { line -> COLLECTION.findAll(line).map { it.groupValues[1] }.toList() }
            .filter { it != "databases" }
            .distinct()
            .map { "`$it`" }
    assertNamed(collections, "Firestore collections")
  }

  @Test
  fun architectureDoc_namesOnlyExistingTypes() {
    val sources =
        File(root, "app/src")
            .walkTopDown()
            .filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }
    val types = TYPE.findAll(architecture).map { it.groupValues[1] }.distinct().toList()
    assertTrue(
        "Found no type names to check; this test's pattern is out of date",
        types.isNotEmpty(),
    )
    val missing = types.filterNot { Regex("""\b$it\b""").containsMatchIn(sources) }
    assertTrue("ARCHITECTURE.md names types missing from app/src: $missing", missing.isEmpty())
  }

  private fun assertNamed(names: List<String>, kind: String) {
    // Guards against passing with nothing checked once the sources move.
    assertTrue("Found no $kind to check; this test's paths are out of date", names.isNotEmpty())
    val missing = names.filterNot { it in architecture }
    assertTrue("ARCHITECTURE.md does not name these $kind: $missing", missing.isEmpty())
  }

  private companion object {
    /**
     * A collection in a `match` path: a literal segment followed by a `{wildcard}`, so nested and
     * collection-group paths count too.
     */
    val COLLECTION = Regex("""/([^/{}\s]+)/\{""")

    /** A capitalized Kotlin name alone in backticks, such as a class or an enum constant. */
    val TYPE = Regex("`([A-Z][A-Za-z0-9_]*)`")
  }
}
