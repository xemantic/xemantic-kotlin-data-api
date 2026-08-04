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

package com.xemantic.kotlin.data.api.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * The configured [DataApiFirIdeMode], absent when the consumer did not pick one — in which case
 * [DataApiFirIdeMode.DEFAULT] applies.
 */
val FIR_IDE_MODE_KEY: CompilerConfigurationKey<DataApiFirIdeMode> =
    CompilerConfigurationKey.create("@DataApi FIR extensions to run in the IDE")

/**
 * Declares the command line options accepted by the plugin.
 */
@OptIn(ExperimentalCompilerApi::class)
class DataApiCommandLineProcessor : CommandLineProcessor {

    override val pluginId: String = DATA_API_PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = listOf(FIR_IDE_MODE_OPTION)

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option) {
            FIR_IDE_MODE_OPTION -> configuration.put(
                FIR_IDE_MODE_KEY,
                DataApiFirIdeMode.ofCliValue(value) ?: throw CliOptionProcessingException(
                    "Unknown '$FIR_IDE_MODE_OPTION_NAME' value: '$value', " +
                        "expected one of: ${DataApiFirIdeMode.cliValues}"
                )
            )
            else -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
        }
    }

}

internal const val FIR_IDE_MODE_OPTION_NAME = "firIdeMode"

private val FIR_IDE_MODE_OPTION = CliOption(
    optionName = FIR_IDE_MODE_OPTION_NAME,
    valueDescription = DataApiFirIdeMode.cliValues,
    description = "which FIR extensions to run in a non-CLI (IDE) session; " +
        "a CLI compilation always runs all of them",
    required = false,
    allowMultipleOccurrences = false
)
