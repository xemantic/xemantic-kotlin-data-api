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

package com.xemantic.kotlin.data.api.compiler.fir

import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers
import org.jetbrains.kotlin.psi.KtDeclaration

/**
 * The diagnostics the `@DataApi` plugin reports, registered with the compiler by
 * `DataApiFirExtensionRegistrar` so that they render with their message rather than as an
 * unknown factory id.
 */
internal object DataApiErrors : KtDiagnosticsContainer() {

    /** The annotated class is not one `@DataApi` can generate for; see [dataApiViolation]. */
    val UNSUPPORTED_DATA_API_CLASS: KtDiagnosticFactory1<String> by error1<KtDeclaration, String>(
        SourceElementPositioningStrategies.DECLARATION_NAME
    )

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = DataApiErrorMessages

}

private object DataApiErrorMessages : BaseDiagnosticRendererFactory() {

    override val MAP: KtDiagnosticFactoryToRendererMap by KtDiagnosticFactoryToRendererMap(
        "DataApiErrors"
    ) { map ->
        map.put(
            DataApiErrors.UNSUPPORTED_DATA_API_CLASS,
            "{0}",
            CommonRenderers.STRING
        )
    }

}
