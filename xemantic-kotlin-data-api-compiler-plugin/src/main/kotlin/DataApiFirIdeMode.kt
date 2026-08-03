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

package com.xemantic.kotlin.data.api.compiler

/**
 * Selects which of the plugin's FIR extensions run in a **non-CLI** session, which in practice
 * means the IntelliJ analyzer. A CLI compilation always runs all of them, whatever this is set to,
 * so the mode can never change the code that is actually produced — only what the editor sees.
 *
 * It exists because the IDE resolves with the Kotlin compiler bundled in the *IDE build*, not the
 * one the project compiles with, and not the one this plugin was compiled against. The FIR
 * declaration-generation API is unstable across those versions, so a plugin that generates cleanly
 * under the CLI can still throw inside an older or newer analyzer — and there the failure is not a
 * diagnostic but an exception, leaving the consumer with red code and IDE error notifications and
 * no way to switch the plugin off short of unapplying it from the build. This is that switch.
 *
 * Note that the IDE runs no plugin at all unless the `kotlin.k2.only.bundled.compiler.plugins.enabled`
 * registry option is off, so for most consumers every mode here looks the same as [NONE].
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
    NONE;

    internal val cliValue: String get() = name.lowercase()

    companion object {

        internal val DEFAULT: DataApiFirIdeMode = ALL

        internal fun ofCliValue(value: String): DataApiFirIdeMode? =
            entries.find { it.cliValue == value }

        internal val cliValues: String get() = entries.joinToString("|") { it.cliValue }

    }

}
