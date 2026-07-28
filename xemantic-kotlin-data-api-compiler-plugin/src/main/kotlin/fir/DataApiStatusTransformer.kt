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

import com.xemantic.kotlin.data.api.compiler.DATA_API_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.transform
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol

/**
 * Lowers the visibility of the constructors of every `@DataApi`-annotated class to `internal`, so
 * that instances can only be created through the generated builder (the nested `Builder` /
 * companion `invoke` synthesized by [DataApiBuilderGenerator]) rather than directly by external
 * callers. Every constructor is lowered, not just the primary one: a secondary constructor left
 * public would hand external callers a construction path bypassing the builder's validation.
 *
 * A constructor the user already declared as narrower than `internal` keeps its own visibility —
 * lowering must never widen what the author wrote.
 */
class DataApiStatusTransformer(
    session: FirSession
) : FirStatusTransformerExtension(session) {

    override fun needTransformStatus(declaration: FirDeclaration): Boolean =
        declaration is FirConstructor

    override fun transformStatus(
        status: FirDeclarationStatus,
        constructor: FirConstructor,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean
    ): FirDeclarationStatus {
        if (containingClass == null) return status
        if (!session.predicateBasedProvider.matches(PREDICATE, containingClass)) return status
        return when (status.visibility) {
            Visibilities.Private, Visibilities.Protected, Visibilities.Internal -> status
            else -> status.transform(visibility = Visibilities.Internal)
        }
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(PREDICATE)
    }

    private companion object {
        val PREDICATE: DeclarationPredicate =
            DeclarationPredicate.create { annotated(DATA_API_ANNOTATION_FQ_NAME) }
    }

}
