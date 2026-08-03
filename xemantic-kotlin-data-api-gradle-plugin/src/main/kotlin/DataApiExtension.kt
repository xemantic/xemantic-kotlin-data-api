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

import org.gradle.api.provider.Property

/**
 * Configures the `@DataApi` toolchain:
 *
 * ```kotlin
 * dataApi {
 *     firIdeMode.set(DataApiFirIdeMode.CHECKERS_ONLY)
 * }
 * ```
 */
interface DataApiExtension {

    /**
     * Which of the plugin's FIR extensions run in a non-CLI session — the IDE analyzer.
     * Defaults to [DataApiFirIdeMode.ALL]. A CLI compilation always runs all of them, so this
     * cannot change the code that is produced, only what the editor sees.
     */
    val firIdeMode: Property<DataApiFirIdeMode>

}

/**
 * Which of the `@DataApi` FIR extensions run in a non-CLI (IDE) session.
 *
 * The IDE resolves with the Kotlin compiler bundled in the *IDE build*, which is neither the one
 * the project compiles with nor the one the plugin was compiled against, and the FIR
 * declaration-generation API is unstable across those versions. A plugin that generates cleanly
 * under the CLI can therefore still throw inside an older or newer analyzer, where the failure
 * surfaces as an IDE exception rather than a diagnostic. Lowering this is the way out of that
 * without unapplying the plugin and losing the build.
 *
 * Note that IntelliJ runs no third-party compiler plugin at all unless the
 * `kotlin.k2.only.bundled.compiler.plugins.enabled` registry option is turned off, so unless that
 * has been done every mode here behaves like [NONE].
 */
enum class DataApiFirIdeMode {

    /**
     * Run declaration generation, constructor privatization and the checkers, as the CLI does.
     * The editor resolves the generated DSL and reports the plugin's own diagnostics.
     */
    ALL,

    /**
     * Run only the checkers, so `@DataApi` misuse is still reported in the editor while nothing is
     * generated. The generated DSL reads as unresolved, exactly as with [NONE] — the difference is
     * that a class the plugin would reject is still underlined where it is declared.
     */
    CHECKERS_ONLY,

    /**
     * Run nothing. The editor sees the class exactly as written, with a public constructor and no
     * builder.
     */
    NONE

}
