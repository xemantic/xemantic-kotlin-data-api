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

package com.xemantic.kotlin.data.api.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.InternalSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

private const val GROUP = "com.xemantic.kotlin"
private const val COMPILER_PLUGIN_ARTIFACT = "xemantic-kotlin-data-api-compiler-plugin"
private const val ANNOTATIONS_ARTIFACT = "xemantic-kotlin-data-api-annotations"

private const val DATA_API_PLUGIN_ID = "com.xemantic.kotlin.data.api"

private const val DATA_API_EXTENSION_NAME = "dataApi"

// must match the option name declared by the compiler plugin's DataApiCommandLineProcessor
private const val FIR_IDE_MODE_OPTION_NAME = "firIdeMode"

// must match the compiler plugin's own DataApiFirIdeMode.DEFAULT, which is what it falls back to
// when the option is absent — this value is never sent
private val DEFAULT_FIR_IDE_MODE = DataApiFirIdeMode.ALL

private const val KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
private const val KOTLIN_ANDROID_PLUGIN_ID = "org.jetbrains.kotlin.android"
private const val KOTLIN_MPP_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"

private val SUPPORTED_KOTLIN_PLUGIN_IDS = listOf(
    KOTLIN_JVM_PLUGIN_ID,
    KOTLIN_ANDROID_PLUGIN_ID,
    KOTLIN_MPP_PLUGIN_ID
)

/**
 * Applies the `@DataApi` toolchain to a consumer project:
 * - registers the K2 compiler plugin (constructor privatization, nested `Builder` and companion
 *   `invoke` generation, data-class members),
 * - adds the `@DataApi` annotations dependency.
 */
// instantiated reflectively by Gradle from the plugin descriptor, not referenced in code
@Suppress("unused")
class DataApiGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        target.extensions
            .create(DATA_API_EXTENSION_NAME, DataApiExtension::class.java)
            .firIdeMode
            .convention(DEFAULT_FIR_IDE_MODE)
        var annotationsWired = false
        fun wireAnnotations(configuration: String) {
            annotationsWired = true
            target.dependencies.add(configuration, artifact(ANNOTATIONS_ARTIFACT))
        }
        target.pluginManager.withPlugin(KOTLIN_JVM_PLUGIN_ID) { wireAnnotations("implementation") }
        target.pluginManager.withPlugin(KOTLIN_ANDROID_PLUGIN_ID) { wireAnnotations("implementation") }
        target.pluginManager.withPlugin(KOTLIN_MPP_PLUGIN_ID) {
            wireAnnotations("commonMainImplementation")
        }
        // the compiler plugin is wired into every compilation, but `@DataApi` itself only reaches
        // the classpath of the Kotlin plugins handled above — fail loudly rather than leave the
        // consumer with an unresolved reference to the annotation
        target.afterEvaluate {
            check(annotationsWired) {
                "The '$DATA_API_PLUGIN_ID' plugin requires one of the following plugins to be " +
                    "applied to project '${target.path}': " +
                    SUPPORTED_KOTLIN_PLUGIN_IDS.joinToString { "'$it'" }
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = DATA_API_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = GROUP,
        artifactId = COMPILER_PLUGIN_ARTIFACT,
        version = DATA_API_VERSION
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val firIdeMode = project.extensions
            .getByType(DataApiExtension::class.java)
            .firIdeMode
        return firIdeMode.map { mode ->
            // the default is not sent at all: it is what the compiler plugin assumes anyway, and a
            // build that never touches the mode must keep compiling against a compiler plugin
            // resolved to a version predating the option, which would reject it as unsupported.
            // `InternalSubpluginOption` is the variant excluded from the compile task's input
            // tracking — a CLI compilation ignores the mode by construction, so tracking it would
            // invalidate every compilation and miss every build cache entry over an editor setting
            // that provably cannot change the output
            if (mode == DEFAULT_FIR_IDE_MODE) emptyList()
            else listOf<SubpluginOption>(
                InternalSubpluginOption(FIR_IDE_MODE_OPTION_NAME, mode.name.lowercase())
            )
        }
    }

}

private fun artifact(name: String): String = "$GROUP:$name:${DATA_API_VERSION}"
