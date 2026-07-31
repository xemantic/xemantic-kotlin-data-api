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
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol

/**
 * Lowers the visibility of the constructors of every `@DataApi`-annotated class to `private`, so
 * that instances can only be created through the generated builder (the nested `Builder` and the
 * factory function synthesized by [DataApiBuilderGenerator]) rather than directly. Every
 * constructor is lowered, not just the primary one: a secondary constructor left public would hand
 * callers a construction path bypassing the builder's validation.
 *
 * `private` rather than `internal`, because `internal` is a Kotlin-only boundary: the JVM backend
 * emits an internal constructor as `ACC_PUBLIC` (unlike an internal *function*, a constructor
 * cannot be name-mangled), leaving the positional constructor callable from Java and so still part
 * of the binary API this plugin exists to retire. The generated `build()` reaches the private
 * constructor from the nested `Builder` through the synthetic accessor the JVM backend generates
 * for exactly this pattern.
 *
 * A class whose shape the plugin cannot generate for keeps its constructors as declared — see
 * [dataApiViolation]. It has no builder to be constructed through, so privatizing it would leave it
 * with no construction path at all, and every call site and subclass would report an inaccessible
 * constructor on top of the one diagnostic explaining what is actually wrong.
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
        if (containingClass !is FirClassSymbol<*>) return status
        if (!session.predicateBasedProvider.matches(PREDICATE, containingClass)) return status
        if (containingClass.dataApiViolation(session) != null) return status
        return if (status.visibility == Visibilities.Private) status
        else status.transform(visibility = Visibilities.Private)
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(PREDICATE)
    }

    private companion object {
        val PREDICATE: DeclarationPredicate =
            DeclarationPredicate.create { annotated(DATA_API_ANNOTATION_FQ_NAME) }
    }

}
