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

// The compiler plugin is published once per Kotlin compiler it was built against, so which
// artifact a consumer needs depends on the Kotlin *they* build with. The set that exists is known
// only at release time, so it is baked in here from the same file the release matrix reads.
@Suppress("UNCHECKED_CAST")
val kotlinCompilers: List<String> = groovy.json.JsonSlurper()
    .parse(rootProject.layout.projectDirectory.file("gradle/kotlinc-compat.json").asFile)
    .let { (it as Map<String, Any>)["kotlinCompilers"] as List<String> }

// Generates a Version.kt holding the project version, so the plugin can reference the matching
// compiler-plugin / annotations artifact coordinates of this build.
val generateVersion = tasks.register("generateDataApiVersion") {
    description = "Generates Version.kt with the build version for resolving matching artifact coordinates"
    group = "build"
    val versionValue = version.toString()
    val compilers = kotlinCompilers
    val outputDir = layout.buildDirectory.dir("generated/dataApiVersion/kotlin")
    inputs.property("version", versionValue)
    inputs.property("kotlinCompilers", compilers)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get()
            .file("com/xemantic/kotlin/data/api/gradle/Version.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.xemantic.kotlin.data.api.gradle

            internal const val DATA_API_VERSION: String = "$versionValue"

            internal val SUPPORTED_KOTLIN_COMPILERS: Set<String> = setOf(
            ${compilers.joinToString("\n") { "    \"$it\"," }}
            )
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    sourceSets.named("main") {
        kotlin.srcDir(generateVersion)
    }
}
