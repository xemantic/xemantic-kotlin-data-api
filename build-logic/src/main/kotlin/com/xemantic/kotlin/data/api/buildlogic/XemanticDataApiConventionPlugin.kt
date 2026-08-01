/*
 * Copyright 2025-2026 Kazimierz Pogoda / Xemantic
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradleExtension
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradlePlugin

/**
 * Shared build conventions for all `xemantic-kotlin-data-api` modules, applied via
 * `id("xemantic.data.api.convention")`. Mirrors the golem-xiv convention plugin pattern.
 */
@Suppress("unused")
class XemanticDataApiConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.doApply()
    }

}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
private fun Project.doApply() {

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val javaTargetVersion = libs.findVersion("javaTarget").get().toString()
    val kotlinTargetVersion = libs.findVersion("kotlinTarget").get().toString()
    val kotlinVersion = KotlinVersion.fromVersion(kotlinTargetVersion)
    val jvmTargetVersion = JvmTarget.fromTarget(javaTargetVersion)

    plugins.apply(PowerAssertGradlePlugin::class.java)
    extensions.configure<PowerAssertGradleExtension> {
        functions.set(
            listOf(
                "kotlin.assert",
                "com.xemantic.kotlin.test.assert",
                "com.xemantic.kotlin.test.have"
            )
        )
    }

    tasks.withType<JavaCompile> {
        options.release.set(javaTargetVersion.toInt())
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    extensions.findByType<KotlinMultiplatformExtension>()?.doConfigure(kotlinVersion, jvmTargetVersion)
    extensions.findByType<KotlinJvmExtension>()?.doConfigure(kotlinVersion, jvmTargetVersion)

}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
private fun KotlinMultiplatformExtension.doConfigure(
    kotlinVersion: KotlinVersion,
    jvmTargetVersion: JvmTarget
) {
    // Set the common options at the extension level so they propagate to the
    // compilation-level `compilerOptions` of *every* target, including the `metadata`
    // compilation that owns `commonMain`. Configuring only the per-compilation
    // compile tasks leaves the metadata compilation at the compiler-default language
    // version, which trips the source-set consistency check (commonMain > jvmMain).
    compilerOptions {
        configureCommons(kotlinVersion)
    }
    jvm {
        compilerOptions {
            configureJvm(jvmTargetVersion)
        }
    }
}

private fun KotlinJvmExtension.doConfigure(
    kotlinVersion: KotlinVersion,
    jvmTargetVersion: JvmTarget
) {
    compilerOptions {
        configureJvm(jvmTargetVersion)
        configureCommons(kotlinVersion)
    }
}

/**
 * No Java toolchain is declared (see https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/),
 * so `jvmTarget` alone would only pin the bytecode version while still resolving against the JDK
 * the build happens to run on. `-Xjdk-release` also limits the visible JDK API, so that building
 * on a newer JDK cannot produce artifacts that fail at runtime on the targeted one.
 */
private fun KotlinJvmCompilerOptions.configureJvm(
    jvmTargetVersion: JvmTarget
) {
    jvmTarget.set(jvmTargetVersion)
    freeCompilerArgs.add("-Xjdk-release=${jvmTargetVersion.target}")
}

private fun KotlinCommonCompilerOptions.configureCommons(
    kotlinVersion: KotlinVersion
) {
    extraWarnings.set(true)
    progressiveMode.set(true)
    languageVersion.set(kotlinVersion)
    apiVersion.set(kotlinVersion)
}
