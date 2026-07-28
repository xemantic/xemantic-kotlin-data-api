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

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.maven.publish)
    id("xemantic.data.api.convention")
}

dependencies {
    // provided at runtime by the Kotlin Gradle plugin the consumer already applies; declaring it
    // as `implementation` would publish a hard dependency on this exact KGP-API version and drag
    // it onto every consumer's buildscript classpath
    compileOnly(libs.kotlin.gradle.plugin.api)
}

gradlePlugin {
    plugins {
        register("dataApi") {
            id = "com.xemantic.kotlin.data.api"
            implementationClass = "com.xemantic.kotlin.data.api.gradle.DataApiGradlePlugin"
        }
    }
}

// Generates a Version.kt holding the project version, so the plugin can reference the matching
// compiler-plugin / annotations artifact coordinates of this build.
val generateVersion = tasks.register("generateDataApiVersion") {
    description = "Generates Version.kt with the build version for resolving matching artifact coordinates"
    group = "build"
    val versionValue = version.toString()
    val outputDir = layout.buildDirectory.dir("generated/dataApiVersion/kotlin")
    inputs.property("version", versionValue)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get()
            .file("com/xemantic/kotlin/data/api/gradle/Version.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.xemantic.kotlin.data.api.gradle

            internal const val DATA_API_VERSION: String = "$versionValue"
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir(generateVersion)
    }
}
