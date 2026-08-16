/*
 * Copyright 2026 Kazimierz Pogoda / Xemantic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xemantic.kotlin.data.api.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.w3c.dom.Element
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Derives the set of Kotlin compilers this project publishes the compiler plugin for.
 *
 * An IDE analyzes with the kotlinc bundled in its own build, and a consumer compiles with the
 * Kotlin they chose; a compiler plugin is binary compatible with neither unless it was built
 * against it. Which compiler an IDE build uses is `kotlincVersion` in intellij-community's
 * `model.properties`, read at the tag of that build — and it changes between patch releases of
 * the same IDE, which is why this is derived rather than maintained by hand.
 *
 * ```
 * ./gradlew kotlincCompat            # verify the README table against the committed data
 * ./gradlew kotlincCompat --derive   # fetch, then rewrite the JSON and the README table
 * ```
 *
 * Deriving reaches the network and depends on what JetBrains published today, so it is a
 * scheduled job rather than part of `check`: a fresh IDE build appearing must not turn an
 * unrelated change red. What `check` runs is the verification, which needs no network.
 */
abstract class KotlinCompilerCompatTask : DefaultTask() {

    @get:Internal
    abstract val compatFile: RegularFileProperty

    @get:Internal
    abstract val readmeFile: RegularFileProperty

    @set:Option(
        option = "derive",
        description = "Fetch the current compilers from JetBrains and rewrite the data instead of verifying it"
    )
    @get:Internal
    var derive: Boolean = false

    @TaskAction
    fun run() {
        val compat = compatFile.get().asFile
        val readme = readmeFile.get().asFile

        val committed = if (compat.exists()) parseCompat(compat.readText()) else Compat()
        val entries = if (derive) {
            logger.lifecycle("Deriving compilers for IDE lines ${committed.supportedIdeLines.joinToString(", ")}")
            derive(committed.supportedIdeLines)
        } else {
            committed.ideCompilers
        }
        if (entries.isEmpty()) {
            throw GradleException("Derived an empty set of compilers - refusing to write it")
        }

        val compatText = renderCompat(committed.copy(ideCompilers = entries))
        val readmeText = readme.readText()
        val updatedReadme = readmeText.replaceTable(renderTable(entries))

        if (derive) {
            compat.writeText(compatText)
            readme.writeText(updatedReadme)
            logger.lifecycle("Wrote ${entries.size} compilers")
            return
        }

        val stale = buildList {
            if (!compat.exists() || compat.readText() != compatText) add(compat.name)
            if (updatedReadme != readmeText) add(readme.name)
        }
        if (stale.isNotEmpty()) {
            throw GradleException(
                "Out of date: ${stale.joinToString(", ")}. Run './gradlew kotlincCompat --derive'"
            )
        }
        logger.lifecycle("Up to date")
    }

    private fun derive(lines: List<String>): List<CompilerEntry> {
        val compilers = linkedMapOf<String, CompilerEntry>()

        fun record(kotlinc: String, ideLine: String, ide: String) {
            val entry = compilers.getOrPut(kotlinc) { CompilerEntry(kotlinc, ideLine, emptyList()) }
            if (ide !in entry.ides) {
                compilers[kotlinc] = entry.copy(ides = entry.ides + ide)
            }
        }

        releasedBuilds(lines).forEach { (number, version) ->
            val kotlinc = kotlincOf("refs/tags/idea/$number")
            logger.lifecycle("  ${number.padEnd(16)} ${version.padEnd(12)} ${kotlinc ?: "(no tag)"}")
            if (kotlinc != null) {
                record(kotlinc, number.substringBefore('.'), "IntelliJ IDEA $version")
            }
        }

        // the head of a line is the patch release being prepared on it, and publishing for it
        // ahead of time is what spares its users a wait once it ships
        lines.forEach { line ->
            val kotlinc = kotlincOf("refs/heads/$line")
            logger.lifecycle("  ${"$line (head)".padEnd(16)} ${"".padEnd(12)} ${kotlinc ?: "(no branch)"}")
            if (kotlinc != null && kotlinc !in compilers) {
                val base = compilers.values
                    .filter { it.ideLine == line }
                    .flatMap { it.ides }
                    .firstOrNull()
                    ?.substringAfterLast(' ')
                    ?.substringBeforeLast('.')
                    ?: line
                record(kotlinc, line, "IntelliJ IDEA $base.x, the patch in preparation")
            }
        }

        // Android Studio reports a stub compiler version of its own, so the only public mapping
        // from its builds to real compilers is the table KEFS maintains - and it is the mapping
        // that decides whether Android developers are covered at all
        androidStudioCompilers().forEach { (build, kotlinc) ->
            record(kotlinc, build.substringBefore('.'), "Android Studio on platform $build")
        }

        return compilers.values
            .filter { entry ->
                // the JetBrains repository keeps only what is current: without the compiler there
                // is nothing to build the artifact against, whatever IDEs still run it
                isPublished(entry.kotlinc).also { published ->
                    if (!published) {
                        logger.lifecycle("  dropping ${entry.kotlinc}: not in intellij-dependencies")
                    }
                }
            }
            .sortedWith(compareByDescending(KOTLINC_ORDER) { it.kotlinc })
    }

    private fun releasedBuilds(lines: List<String>): List<Pair<String, String>> {
        val document = DocumentBuilderFactory.newInstance()
            .also { it.isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(fetch(UPDATES_URL)!!.byteInputStream())
        val product = document.getElementsByTagName("product").let { products ->
            (0 until products.length)
                .map { products.item(it) as Element }
                .first { it.getAttribute("name") == "IntelliJ IDEA" }
        }
        val builds = product.getElementsByTagName("build")
        return (0 until builds.length)
            .map { builds.item(it) as Element }
            .mapNotNull { build ->
                val number = build.getAttribute("fullNumber").ifEmpty { build.getAttribute("number") }
                val version = build.getAttribute("version")
                if (number.substringBefore('.') !in lines || PRERELEASE.containsMatchIn(version)) null
                else number to version
            }
            .distinctBy { it.first }
            .sortedWith(compareByDescending(BUILD_ORDER) { it.first })
    }

    private fun kotlincOf(ref: String): String? = fetch(MODEL_PROPERTIES.format(ref))
        ?.lineSequence()
        ?.firstOrNull { it.startsWith("kotlincVersion=") }
        ?.substringAfter('=')
        ?.trim()

    private fun androidStudioCompilers(): Map<String, String> =
        (fetch(ANDROID_STUDIO_VERSIONS) ?: "")
            .lineSequence()
            .filter { !it.startsWith("#") && it.contains('=') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

    private fun isPublished(version: String): Boolean = fetch(COMPILER_POM.format(version, version)) != null

    private fun fetch(url: String): String? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build()
        val response = HTTP.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            200 -> response.body()
            404 -> null
            else -> throw GradleException("GET $url failed with ${response.statusCode()}")
        }
    }

    private fun renderTable(entries: List<CompilerEntry>): String {
        val width = entries.maxOf { it.kotlinc.length } + 2
        return buildList {
            add("| ${"kotlinc".padEnd(width)} | IDE builds analyzing with it |")
            add("|${"-".repeat(width + 2)}|------------------------------|")
            entries.forEach { entry ->
                add("| ${"`${entry.kotlinc}`".padEnd(width)} | ${entry.ides.joinToString(", ")} |")
            }
        }.joinToString("\n")
    }

    private fun String.replaceTable(table: String): String {
        val start = indexOf(TABLE_START)
        val end = indexOf(TABLE_END)
        if (start < 0 || end < 0) {
            throw GradleException("$TABLE_START / $TABLE_END markers not found in ${readmeFile.get().asFile.name}")
        }
        return substring(0, start) + TABLE_START + "\n" + table + "\n" + substring(end)
    }

    private fun parseCompat(json: String): Compat = Compat(
        supportedIdeLines = json.stringArray("supportedIdeLines"),
        kotlinCompilers = json.stringArray("kotlinCompilers"),
        ideCompilers = OBJECT.findAll(json.substringAfter("\"ideCompilers\"").substringBefore("\"kotlinCompilers\""))
            .map { it.value }
            .map { entry ->
                CompilerEntry(
                    kotlinc = entry.string("kotlinc"),
                    ideLine = entry.string("ideLine"),
                    ides = entry.stringArray("ides")
                )
            }
            .toList()
    )

    private fun renderCompat(compat: Compat): String = buildString {
        appendLine("{")
        appendLine("  \"\$comment\": \"${COMMENT}\",")
        appendLine("  \"supportedIdeLines\": ${jsonArray(compat.supportedIdeLines, indent = 2)},")
        appendLine("  \"ideCompilers\": [")
        compat.ideCompilers.forEachIndexed { index, entry ->
            appendLine("    {")
            appendLine("      \"kotlinc\": \"${entry.kotlinc}\",")
            appendLine("      \"ideLine\": \"${entry.ideLine}\",")
            appendLine("      \"ides\": ${jsonArray(entry.ides, indent = 6)}")
            appendLine("    }" + if (index == compat.ideCompilers.lastIndex) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"kotlinCompilers\": ${jsonArray(compat.kotlinCompilers, indent = 2)}")
        appendLine("}")
    }

    private fun jsonArray(values: List<String>, indent: Int): String =
        if (values.isEmpty()) "[]"
        else values.joinToString(
            separator = ",\n${" ".repeat(indent + 2)}",
            prefix = "[\n${" ".repeat(indent + 2)}",
            postfix = "\n${" ".repeat(indent)}]"
        ) { "\"$it\"" }

    private data class Compat(
        val supportedIdeLines: List<String> = listOf("262", "261"),
        val ideCompilers: List<CompilerEntry> = emptyList(),
        val kotlinCompilers: List<String> = emptyList()
    )

    private data class CompilerEntry(
        val kotlinc: String,
        val ideLine: String,
        val ides: List<String>
    )

    private companion object {

        const val UPDATES_URL = "https://www.jetbrains.com/updates/updates.xml"

        const val MODEL_PROPERTIES = "https://raw.githubusercontent.com/JetBrains/intellij-community/%s" +
            "/plugins/kotlin/util/project-model-updater/resources/model.properties"

        // Android Studio's own `compiler.version` is a stub (X.Y.255-dev-Z), so this table,
        // maintained by KEFS from a JetBrains source needing a Space token, is the reachable
        // mapping of its builds
        const val ANDROID_STUDIO_VERSIONS = "https://raw.githubusercontent.com/Mr3zee/" +
            "Kotlin-External-FIR-Support/main/src/main/resources/android-studio-kotlin-versions.properties"

        // the only repository publishing the compilers IDE builds are made with; note that it
        // prunes them, so a compiler an old IDE still runs may no longer be buildable against
        const val COMPILER_POM = "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies" +
            "/org/jetbrains/kotlin/kotlin-compiler-embeddable/%s/kotlin-compiler-embeddable-%s.pom"

        const val TABLE_START = "<!-- kotlinc-compat:start -->"
        const val TABLE_END = "<!-- kotlinc-compat:end -->"

        const val COMMENT = "Generated by the 'kotlincCompat' task - do not edit ideCompilers by hand. " +
            "Every entry is published as an artifact of the compiler plugin at release."

        // builds whose IDE version carries any of these are not released to users; the compiler of
        // a pre-release build reaches the matrix only when a released build of the line shares it
        val PRERELEASE = Regex("EAP|Beta|Preview|Release Candidate|RC", RegexOption.IGNORE_CASE)

        val OBJECT = Regex("\\{[^{}]*}", RegexOption.DOT_MATCHES_ALL)

        val HTTP: HttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

        val BUILD_ORDER = Comparator<String> { left, right ->
            val leftParts = left.split('.').map { it.toIntOrNull() ?: 0 }
            val rightParts = right.split('.').map { it.toIntOrNull() ?: 0 }
            compareLists(leftParts, rightParts)
        }

        /**
         * An `-ijNNN-NN` compiler is always newer than the `-dev-NNNN` one of the same Kotlin
         * version: a line starts on a dev build and switches to `-ij` once it has its own branch.
         */
        val KOTLINC_ORDER = Comparator<String> { left, right ->
            fun key(version: String): Triple<List<Int>, Int, Int> = Triple(
                version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 },
                if (version.contains("-ij")) 1 else 0,
                version.substringAfterLast('-').toIntOrNull() ?: 0
            )
            val (leftNumbers, leftIj, leftSuffix) = key(left)
            val (rightNumbers, rightIj, rightSuffix) = key(right)
            compareLists(leftNumbers, rightNumbers)
                .takeIf { it != 0 }
                ?: compareValues(leftIj, rightIj).takeIf { it != 0 }
                ?: compareValues(leftSuffix, rightSuffix)
        }

        fun compareLists(left: List<Int>, right: List<Int>): Int {
            (0 until maxOf(left.size, right.size)).forEach { index ->
                val comparison = compareValues(left.getOrElse(index) { 0 }, right.getOrElse(index) { 0 })
                if (comparison != 0) return comparison
            }
            return 0
        }

        fun String.string(key: String): String =
            Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(this)?.groupValues?.get(1) ?: ""

        fun String.stringArray(key: String): List<String> =
            Regex("\"$key\"\\s*:\\s*\\[([^\\]]*)]", RegexOption.DOT_MATCHES_ALL)
                .find(this)
                ?.groupValues
                ?.get(1)
                ?.let { Regex("\"([^\"]*)\"").findAll(it).map { match -> match.groupValues[1] }.toList() }
                ?: emptyList()
    }

}
