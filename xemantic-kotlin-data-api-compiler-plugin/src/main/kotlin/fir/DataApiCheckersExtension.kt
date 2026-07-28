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

import com.xemantic.kotlin.data.api.compiler.DATA_API_ANNOTATION_CLASS_ID
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation

/**
 * Rejects, at compile time, every `@DataApi` class whose shape the plugin cannot generate for,
 * so that an unsupported class is a clearly explained error on the annotation rather than a
 * compiler crash, broken bytecode or a runtime failure deep inside the generated `build()`.
 */
class DataApiCheckersExtension(
    session: FirSession
) : FirAdditionalCheckersExtension(session) {

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val regularClassCheckers: Set<FirRegularClassChecker> =
            setOf(DataApiClassChecker)
    }

}

private object DataApiClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        val symbol = declaration.symbol
        if (!symbol.hasAnnotation(DATA_API_ANNOTATION_CLASS_ID, context.session)) return
        val violation = symbol.dataApiViolation() ?: return
        reporter.reportOn(
            declaration.source,
            DataApiErrors.UNSUPPORTED_DATA_API_CLASS,
            violation
        )
    }

}
