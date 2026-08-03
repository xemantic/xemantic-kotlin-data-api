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

@file:OptIn(ExperimentalCompilerApi::class)

package com.xemantic.kotlin.data.api.compiler

import com.tschuchort.compiletesting.DiagnosticSeverity
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.ByteArrayOutputStream

/**
 * Runs the `@DataApi` compiler plugin over a source snippet, in this JVM, and reports what it said
 * about it.
 *
 * The `xemantic-kotlin-data-api-test` module can only exercise code that *compiles*; the other half
 * of this plugin is what it refuses to compile, and a rejected class shape can only be asserted by
 * reading the diagnostic the compiler emits for it. That half matters as much as the generated code:
 * every shape rejected here is one that would otherwise crash the compiler or produce a class no
 * caller could construct, and the diagnostic is the only thing standing between the user and an
 * internal compiler error.
 */
internal fun compile(
    code: String,
    firIdeMode: DataApiFirIdeMode? = null
): DataApiCompilation {
    val messages = ByteArrayOutputStream()
    val result = KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin("Sample.kt", SAMPLE_PREAMBLE + code))
        compilerPluginRegistrars = listOf(DataApiCompilerPluginRegistrar())
        if (firIdeMode != null) {
            commandLineProcessors = listOf(DataApiCommandLineProcessor())
            pluginOptions = listOf(
                PluginOption(DATA_API_PLUGIN_ID, "firIdeMode", firIdeMode.name.lowercase())
            )
        }
        // the snippets refer to `@DataApi` and, where relevant, to kotlin.test — both of which are
        // on this module's own test classpath
        inheritClassPath = true
        messageOutputStream = messages
    }.compile()
    return DataApiCompilation(
        succeeded = result.exitCode == KotlinCompilation.ExitCode.OK,
        errors = result.diagnosticMessages
            .filter { it.severity == DiagnosticSeverity.ERROR }
            .map { it.message.trim() }
    )
}

private const val SAMPLE_PREAMBLE =
    "import com.xemantic.kotlin.data.api.DataApi\n"

/**
 * What the compiler made of a snippet: whether it compiled at all, and every error it reported.
 *
 * Assertions are on the *whole* error list rather than on "contains", because half of what these
 * tests are for is that a rejected class produces **one** diagnostic and not a cascade — a class
 * generated for but not privatized, or the other way round, reports a shape violation buried under
 * unresolved references and inaccessible constructors.
 */
internal class DataApiCompilation(
    val succeeded: Boolean,
    val errors: List<String>
) {

    /** The single error reported, failing when the snippet produced none or more than one. */
    val error: String
        get() = errors.singleOrNull()
            ?: error("expected exactly 1 error, got ${errors.size}: $errors")

}
