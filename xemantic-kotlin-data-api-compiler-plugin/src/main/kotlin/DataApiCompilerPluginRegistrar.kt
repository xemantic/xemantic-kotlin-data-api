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

import com.xemantic.kotlin.data.api.compiler.fir.DataApiFirExtensionRegistrar
import com.xemantic.kotlin.data.api.compiler.ir.DataApiIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/**
 * Registers the K2 frontend (FIR) and backend (IR) extensions of the `@DataApi` plugin.
 */
@OptIn(ExperimentalCompilerApi::class)
class DataApiCompilerPluginRegistrar : CompilerPluginRegistrar() {

    override val pluginId: String = DATA_API_PLUGIN_ID

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(DataApiFirExtensionRegistrar())
        IrGenerationExtension.registerExtension(DataApiIrGenerationExtension())
    }

}
