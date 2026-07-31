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

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * The compiler plugin id, shared with the Gradle plugin and the command line processor.
 */
const val DATA_API_PLUGIN_ID: String = "com.xemantic.kotlin.data.api"

internal val DATA_API_PACKAGE: FqName = FqName("com.xemantic.kotlin.data.api")

internal val DATA_API_ANNOTATION_FQ_NAME: FqName = DATA_API_PACKAGE.child(Name.identifier("DataApi"))

internal val DATA_API_ANNOTATION_CLASS_ID: ClassId =
    ClassId(DATA_API_PACKAGE, Name.identifier("DataApi"))

/**
 * The `@DslMarker` annotation stamped on every generated `Builder`, confining a builder DSL block
 * to its own receiver instead of letting an unresolved assignment fall through to the enclosing
 * builder.
 */
internal val DATA_API_DSL_ANNOTATION_CLASS_ID: ClassId =
    ClassId(DATA_API_PACKAGE, Name.identifier("DataApiDsl"))

internal val BUILDER_NAME: Name = Name.identifier("Builder")

/**
 * The name of the private `Builder` flag recording that [property] was assigned, generated for
 * every property whose value alone cannot say whether it was — one with a default value, and one
 * typed as a bare type parameter. The `$` keeps it out of reach of any user-declared property name,
 * which cannot contain one.
 */
internal fun assignedFlagName(property: Name): Name =
    Name.identifier($$"$${property.asString()}$isAssigned")
