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
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.isLocal
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

/**
 * The class shapes `@DataApi` can generate for, expressed as the reason a given class is *not* one
 * of them — `null` meaning the class is supported.
 *
 * The shapes rejected here are exactly those the generated code cannot express: a class the
 * `Builder` could not instantiate, a member it would clash with, or a constructor parameter it
 * could not carry. [DataApiCheckersExtension] turns the reason into a compile-time error on the
 * annotated class, and [DataApiBuilderGenerator] consults it too, so that an unsupported class is
 * left untouched rather than half-generated — which is what turns these shapes into an obscure
 * compiler crash or broken bytecode instead of a diagnostic.
 *
 * Every check reads the *raw* declaration status only, so it stays safe to call from the generation
 * extension, well before types are resolved.
 */
@OptIn(DirectDeclarationsAccess::class)
internal fun FirClassSymbol<*>.dataApiViolation(): String? {

    if (classKind != ClassKind.CLASS) return "'@DataApi' is applicable only to a class, " +
        "but '${classId.shortClassName}' is declared as " +
        "'${classKind.codeRepresentation ?: "enum entry"}'"
    if (isInner) return "'@DataApi' is not applicable to an inner class"
    if (isLocal) return "'@DataApi' is not applicable to a local class"
    when (rawStatus.modality) {
        // the generated `build()` instantiates the class, which an abstract one cannot be
        Modality.ABSTRACT -> return "'@DataApi' is not applicable to an abstract class"
        Modality.SEALED -> return "'@DataApi' is not applicable to a sealed class"
        else -> {}
    }
    if (typeParameterSymbols.isNotEmpty()) {
        return "'@DataApi' is not applicable to a generic class"
    }

    val nestedClasses = declarationSymbols.filterIsInstance<FirRegularClassSymbol>()
    if (nestedClasses.any { it.isCompanion }) {
        return "'@DataApi' generates a companion object, " +
            "but '${classId.shortClassName}' already declares one"
    }
    if (nestedClasses.any { it.name == BUILDER_NAME }) {
        return "'@DataApi' generates a nested '${BUILDER_NAME.asString()}' class, " +
            "but '${classId.shortClassName}' already declares one"
    }

    val constructor = declarationSymbols
        .filterIsInstance<FirConstructorSymbol>()
        .firstOrNull { it.isPrimary }
        ?: return "'@DataApi' requires a primary constructor, " +
            "which '${classId.shortClassName}' does not declare"

    val propertyNames = declarationSymbols
        .filterIsInstance<FirPropertySymbol>()
        .mapTo(mutableSetOf()) { it.name }
    constructor.valueParameterSymbols.forEach { parameter ->
        // the generated members are built from the class's properties, so a parameter that
        // declares none has nothing to generate from
        if (parameter.name !in propertyNames) {
            return "'@DataApi' requires every primary constructor parameter to declare a property, " +
                "but '${parameter.name.asString()}' is neither a 'val' nor a 'var'"
        }
        // a vararg is omissible at the constructor, a distinction the builder cannot express
        if (parameter.isVararg) {
            return "'@DataApi' does not support a 'vararg' primary constructor parameter, " +
                "but '${parameter.name.asString()}' is one"
        }
    }

    return null
}
