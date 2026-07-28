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

@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    alias(libs.plugins.maven.publish)
    id("xemantic.data.api.convention")
}

kotlin {

    explicitApi()

    js {
        browser()
        nodejs()
        binaries.library()
    }

    wasmJs {
        browser()
        nodejs()
        d8()
        binaries.library()
    }

    wasmWasi {
        nodejs()
        binaries.library()
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

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
            }
        }

    }

}

// skip tests which require XCode components to be installed
tasks {
    named("tvosSimulatorArm64Test") { enabled = false }
    named("watchosSimulatorArm64Test") { enabled = false }
}
