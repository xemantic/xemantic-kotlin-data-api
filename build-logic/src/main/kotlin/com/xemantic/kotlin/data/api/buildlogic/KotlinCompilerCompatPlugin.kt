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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Contributes the `kotlincCompat` task, which maintains the set of Kotlin compilers the compiler
 * plugin is published for. Applied to the root project, since both files it owns live there.
 */
@Suppress("unused")
class KotlinCompilerCompatPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val kotlincCompat = target.tasks.register<KotlinCompilerCompatTask>("kotlincCompat") {
            group = "verification"
            description = "Verifies the Kotlin compiler compatibility data, or rewrites it with --derive"
            compatFile.convention(target.layout.projectDirectory.file("gradle/kotlinc-compat.json"))
            readmeFile.convention(target.layout.projectDirectory.file("README.md"))
            // it compares, and with --derive rewrites, checked-in files; caching either is meaningless
            outputs.upToDateWhen { false }
        }
        // in its default mode it only compares two checked-in files and needs no network, which is
        // what makes it safe to run on every build - unlike --derive, whose result depends on what
        // JetBrains published today
        target.plugins.withId("base") {
            target.tasks.named("check") { dependsOn(kotlincCompat) }
        }
    }

}
