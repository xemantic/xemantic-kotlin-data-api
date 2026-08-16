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

@file:Suppress("RedundantSuppression")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
    id("xemantic.data.api.convention")
}

@Suppress("AvoidDuplicateDependencies")
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    // the diagnostics tests run the compiler in-process, so they need it on the test classpath,
    // together with the `@DataApi` annotation the compiled snippets carry
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(project(":xemantic-kotlin-data-api-annotations"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.xemantic.kotlin.test)
}

// A compiler plugin is binary compatible only with the compiler it was built against, and the two
// consumers of this one do not agree on which that is: an IDE analyzes with the kotlinc bundled in
// its own build, a consumer's build uses the Kotlin they chose. `-PkotlincVersion=<version>` builds
// the artifact for one of them — the same sources against that compiler, published as
// `<kotlincVersion>-<version>`, the coordinate KEFS looks up for an IDE and DataApiGradlePlugin
// resolves for a consumer. The set worth publishing lives in gradle/kotlinc-compat.json.
// See the "IDE support" section of README.md.
val kotlincVersion: String? = providers.gradleProperty("kotlincVersion").orNull

if (kotlincVersion != null) {

    version = "$kotlincVersion-$version"

    repositories {
        maven {
            name = "intellijDependencies"
            url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
            // IDE builds of kotlinc are published nowhere else, and everything else must keep
            // resolving from Maven Central. The filter is by version rather than by module,
            // because kotlin-compiler-embeddable pulls its own kotlin-build-tools-api at the very
            // same IDE version, which Maven Central does not have either
            content { includeVersionByRegex("org\\.jetbrains\\.kotlin", ".*", ".*-(ij\\d+|dev)-\\d+") }
        }
    }

    // strictly this module's own classpaths: the Kotlin Gradle plugin resolves
    // kotlin-compiler-embeddable for the compiler daemon it runs too, and substituting it there
    // fails the build with a bare "Daemon compilation failed"
    configurations
        .matching { it.name in setOf("compileClasspath", "testCompileClasspath", "testRuntimeClasspath") }
        .configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-compiler-embeddable") {
                    useVersion(kotlincVersion)
                    because("the plugin must be binary compatible with the compiler that will load it")
                }
            }
        }

}
