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

import com.xemantic.kotlin.data.api.compiler.BUILDER_NAME
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirImplicitNullableAnyTypeRef
import org.jetbrains.kotlin.fir.types.isNullableAny
import org.jetbrains.kotlin.types.Variance

/**
 * The class shapes `@DataApi` can generate for, expressed as the reason a given class is *not* one
 * of them — `null` meaning the class is supported.
 *
 * The shapes rejected here are exactly those the generated code cannot express: a class the
 * `Builder` could not instantiate, a member it would clash with, a constructor parameter it could
 * not carry, or a factory function with nowhere to go. [DataApiCheckersExtension] turns the reason
 * into a compile-time error on the annotated class, [DataApiBuilderGenerator] consults it so that an
 * unsupported class is left untouched rather than half-generated, and [DataApiStatusTransformer]
 * consults it so that such a class keeps its public constructor. All three have to agree: a class
 * generated for but not privatized, or privatized but not generated for, produces a cascade of
 * follow-on errors that buries the one diagnostic the user is meant to read.
 *
 * Every check reads the *raw* declaration status only, so it stays safe to call from the generation
 * extension, well before types are resolved.
 */
@OptIn(DirectDeclarationsAccess::class)
internal fun FirClassSymbol<*>.dataApiViolation(session: FirSession): String? {

    if (classKind != ClassKind.CLASS) return "'@DataApi' is applicable only to a class, " +
        "but '${classId.shortClassName}' is declared as " +
        "'${classKind.codeRepresentation ?: "enum entry"}'"
    if (isInner) return "'@DataApi' is not applicable to an inner class"
    if (isLocal) return "'@DataApi' is not applicable to a local class"
    when (rawStatus.modality) {
        // the generated `build()` instantiates the class, which an abstract one cannot be
        Modality.ABSTRACT -> return "'@DataApi' is not applicable to an abstract class"
        Modality.SEALED -> return "'@DataApi' is not applicable to a sealed class"
        // the generated `equals` tests `other is Person`, exactly as a `data` class does, which
        // stays correct only while no subclass can pass that test — the very reason a `data` class
        // is final
        Modality.OPEN -> return "'@DataApi' is not applicable to an open class"
        else -> {}
    }
    // `@DataApi` generates the very members the compiler generates for a `data` or `value` class —
    // `equals`/`hashCode`/`toString`, and a `copy` of an incompatible shape. Left to collide, the
    // two generators do not produce a diagnostic but crash Fir2Ir, which picks up the plugin's
    // `copy(block: Builder.() -> Unit)` as the data class's own `copy` and fails looking for the
    // constructor parameter it corresponds to
    if (rawStatus.isData) return "'@DataApi' is not applicable to a 'data' class — it generates " +
        "'equals', 'hashCode', 'toString' and 'copy' itself; drop the 'data' modifier"
    if (rawStatus.isValue || rawStatus.isInline) {
        return "'@DataApi' is not applicable to a 'value' class — it generates 'equals', " +
            "'hashCode' and 'toString' itself, which a 'value' class generates too"
    }
    typeParameterSymbols.forEach { parameter ->
        // `var value: T?` in the builder is an `in` position, which a covariant parameter cannot
        // occupy, and a contravariant one could not be read back out of
        if (parameter.variance != Variance.INVARIANT) {
            return "'@DataApi' does not support a type parameter with declaration-site variance, " +
                "but '${parameter.name.asString()}' is declared '${parameter.variance.label}'"
        }
        // the builder mirrors the class's type parameters onto its own, and the bounds would have
        // to be mirrored with them — but nested classes are generated during supertype resolution,
        // before any bound has a resolved type to copy
        if (parameter.hasDeclaredBound()) {
            return "'@DataApi' does not support a type parameter with an upper bound, " +
                "but '${parameter.name.asString()}' declares one"
        }
    }

    if (declaredNestedClasses().any { it.name == BUILDER_NAME }) {
        return "'@DataApi' generates a nested '${BUILDER_NAME.asString()}' class, " +
            "but '${classId.shortClassName}' already declares one"
    }

    factoryHostViolation(session)?.let { return it }

    val constructor = declarationSymbols
        .filterIsInstance<FirConstructorSymbol>()
        .firstOrNull { it.isPrimary }
        ?: return "'@DataApi' requires a primary constructor, " +
            "which '${classId.shortClassName}' does not declare"

    val propertyVisibilities = declarationSymbols
        .filterIsInstance<FirPropertySymbol>()
        .associate { it.name to it.rawStatus.visibility }
    constructor.valueParameterSymbols.forEach { parameter ->
        // the generated members are built from the class's properties, so a parameter that
        // declares none has nothing to generate from
        if (parameter.name !in propertyVisibilities) {
            return "'@DataApi' requires every primary constructor parameter to declare a property, " +
                "but '${parameter.name.asString()}' is neither a 'val' nor a 'var'"
        }
        // a vararg is omissible at the constructor, a distinction the builder cannot express
        if (parameter.isVararg) {
            return "'@DataApi' does not support a 'vararg' primary constructor parameter, " +
                "but '${parameter.name.asString()}' is one"
        }
        // the builder mirrors each property at its declared visibility, so that a hidden property
        // does not become settable by everyone. A private one is therefore assignable by nothing at
        // all — which is fine only while the constructor has a value to fall back on, since the
        // constructor itself is private too and leaves no other way in
        if (
            propertyVisibilities[parameter.name] == Visibilities.Private &&
            !parameter.hasDefaultValue
        ) {
            return "'@DataApi' requires a 'private' primary constructor property to have a default " +
                "value, but '${parameter.name.asString()}' has none — the builder property " +
                "mirroring it is private too, so nothing outside the builder could ever assign it"
        }
    }

    return null
}

/**
 * The reason the factory function of this class has nowhere to go — `null` when it has.
 *
 * A top-level class always has one: the factory is generated next to it. A *nested* class's factory
 * goes into the enclosing type, so that `Outer.Inner { … }` keeps reading like the constructor it
 * replaces — into the enclosing object itself, or into the enclosing class's companion object.
 *
 * The companion has to be one the enclosing class *declares*. Generating it here would silently add
 * a public `Companion` to a class its author never annotated — part of that class's binary API from
 * then on — and would collide outright with any other plugin contributing a companion to the same
 * class (kotlinx.serialization for `serializer()`, among others), which is not a diagnostic but an
 * `IllegalStateException` out of the compiler.
 */
@OptIn(DirectDeclarationsAccess::class)
private fun FirClassSymbol<*>.factoryHostViolation(session: FirSession): String? {
    val outerClassId = classId.outerClassId ?: return null
    val outerName = outerClassId.shortClassName.asString()
    val outer = session.symbolProvider
        .getClassLikeSymbolByClassId(outerClassId) as? FirRegularClassSymbol
        ?: return null
    // an object — including a companion object, which cannot have a companion of its own — hosts
    // the factories of the classes nested in it directly
    if (outer.classKind == ClassKind.OBJECT) return null
    if (outer.declaredNestedClasses().none { it.isCompanion }) {
        return "'@DataApi' generates the factory function of a nested class into the enclosing " +
            "class's companion object, but '$outerName' declares none — add a 'companion object' " +
            "to '$outerName'"
    }
    return null
}

/**
 * Whether this type parameter declares an upper bound of its own.
 *
 * Read through the *shape* of the bound reference rather than its type, so that the answer is the
 * same before and after type resolution: an undeclared bound is the implicit `Any?` the FIR builder
 * puts there, a declared one is anything else — a user type reference early on, a resolved type
 * later. Asking for the resolved bound instead would report the violation only from the checker and
 * not from the generator, which runs long before resolution.
 */
@OptIn(SymbolInternals::class)
private fun FirTypeParameterSymbol.hasDeclaredBound(): Boolean =
    fir.bounds.any { !it.isDefaultBound() }

/**
 * Whether this bound is the `Any?` every unbounded type parameter has anyway — left implicit by the
 * FIR builder, or written out by hand, which is the same parameter and needs no mirroring either.
 *
 * The written-out form is recognised syntactically, since this has to answer the same before and
 * after resolution. That leaves a type *alias* for `Any?` reading as a declared bound, which is only
 * conservative — the class is rejected rather than mis-generated.
 */
private fun FirTypeRef.isDefaultBound(): Boolean = when (this) {
    is FirImplicitNullableAnyTypeRef -> true
    is FirResolvedTypeRef -> coneType.isNullableAny
    is FirUserTypeRef -> isMarkedNullable &&
        qualifier.last().let { last ->
            last.name == StandardNames.FqNames.any.shortName() &&
                last.typeArgumentList.typeArguments.isEmpty()
        } &&
        qualifier.dropLast(1).map { it.name.asString() }
            .let { it.isEmpty() || it == listOf(StandardNames.BUILT_INS_PACKAGE_NAME.asString()) }
    else -> false
}

/**
 * The classes this class declares in its own body, read raw so that it stays safe to ask well before
 * resolution. Shared by every consumer of the shape rules, which have to agree on what counts as a
 * declared nested class — most of all on whether a companion object is there.
 */
@OptIn(DirectDeclarationsAccess::class)
internal fun FirClassSymbol<*>.declaredNestedClasses(): List<FirRegularClassSymbol> =
    declarationSymbols.filterIsInstance<FirRegularClassSymbol>()
