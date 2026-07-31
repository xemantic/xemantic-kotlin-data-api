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

@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// End-to-end consumer module for the @DataApi toolchain. Not published. It wires the toolchain
// the same way DataApiGradlePlugin would for a real consumer, but with project dependencies so
// the build exercises the in-repo compiler plugin directly across every target:
//  - commonMainImplementation(annotations)   -> the @DataApi annotation,
//  - kotlinCompilerPluginClasspath(compiler)  -> FIR constructor privatization + nested Builder
//                                                generation (with IR-generated bodies), added to
//                                                every compilation's compiler-plugin classpath.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("xemantic.data.api.convention")
}

kotlin {

    jvm()

    js {
        browser()
        nodejs()
    }

    wasmJs {
        browser()
        nodejs()
        d8()
    }

    wasmWasi {
        nodejs()
    }

    // native, see https://kotlinlang.org/docs/native-target-support.html
    // tier 1
    macosArm64()
    iosSimulatorArm64()
    iosX64()
    iosArm64()

    // tier 2
    linuxX64()
    linuxArm64()
    watchosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosArm64()

    // tier 3
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
    mingwX64()
    watchosDeviceArm64()

    sourceSets {

        commonMain {
            dependencies {
                implementation(project(":xemantic-kotlin-data-api-annotations"))
                // the @DataApi and the kotlinx.serialization compiler plugins both want to
                // contribute a companion object to the same class, which is the interop this
                // module has to keep exercising
                implementation(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.reflect)
            }
        }

    }

}

// Exercise the in-repo compiler plugin directly: add it to every compilation's compiler-plugin
// classpath (one such configuration is created per target/compilation by the KMP plugin).
dependencies {
    configurations
        .matching { it.name.startsWith("kotlinCompilerPluginClasspath") }
        .configureEach {
            add(name, project(":xemantic-kotlin-data-api-compiler-plugin"))
        }
}

// skip tests which require XCode components to be installed
tasks {
    named("tvosSimulatorArm64Test") { enabled = false }
    named("watchosSimulatorArm64Test") { enabled = false }
}
