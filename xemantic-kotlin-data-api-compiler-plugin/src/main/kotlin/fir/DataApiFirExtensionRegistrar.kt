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

package com.xemantic.kotlin.data.api.compiler.fir

import com.xemantic.kotlin.data.api.compiler.DataApiFirIdeMode
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.java.FirCliSession

/**
 * Wires the FIR (frontend) extensions of the `@DataApi` plugin.
 *
 * Each extension is registered through a factory rather than a constructor reference, because
 * whether it does anything is decided per *session*: a CLI compilation always gets the real
 * extension, while an IDE session gets whatever [firIdeMode] allows. Registration itself is
 * unconditional — the compiler builds the extension set once per registrar, so a session that is
 * meant to opt out has to be handed an extension that does nothing rather than none at all.
 */
class DataApiFirExtensionRegistrar(
    private val firIdeMode: DataApiFirIdeMode = DataApiFirIdeMode.DEFAULT
) : FirExtensionRegistrar() {

    override fun ExtensionRegistrarContext.configurePlugin() {
        // the status transformer follows the generator and never runs without it: privatizing the
        // constructors of a class whose builder was not generated leaves it with no construction
        // path at all, so every call site would report an inaccessible constructor — strictly
        // worse than the unresolved references that not generating produces on its own
        +FirStatusTransformerExtension.Factory { session ->
            if (session.generates) DataApiStatusTransformer(session)
            else NoOpStatusTransformer(session)
        }
        +FirDeclarationGenerationExtension.Factory { session ->
            if (session.generates) DataApiBuilderGenerator(session)
            else NoOpDeclarationGenerator(session)
        }
        +FirAdditionalCheckersExtension.Factory { session ->
            if (session.checks) DataApiCheckersExtension(session)
            else NoOpCheckersExtension(session)
        }
        registerDiagnosticContainers(DataApiErrors)
    }

    private val FirSession.generates: Boolean
        get() = isCli || firIdeMode == DataApiFirIdeMode.ALL

    private val FirSession.checks: Boolean
        get() = isCli || firIdeMode != DataApiFirIdeMode.NONE

}

private val FirSession.isCli: Boolean get() = this is FirCliSession

private class NoOpStatusTransformer(
    session: FirSession
) : FirStatusTransformerExtension(session) {

    override fun needTransformStatus(declaration: FirDeclaration): Boolean = false

}

private class NoOpDeclarationGenerator(
    session: FirSession
) : FirDeclarationGenerationExtension(session)

private class NoOpCheckersExtension(
    session: FirSession
) : FirAdditionalCheckersExtension(session)
