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
    alias(libs.plugins.maven.publish)
    id("xemantic.data.api.convention")
}

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
