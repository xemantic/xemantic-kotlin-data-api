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

package com.xemantic.kotlin.data.api.test

import com.xemantic.kotlin.data.api.DataApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A sealed hierarchy whose concrete leaves are `@DataApi` classes — the shape of a polymorphic API
 * payload. The sealed base itself cannot carry the annotation, having no instances of its own to
 * build, but it is the leaves that hold the properties anyway.
 */
sealed class Shape {

    abstract val id: String

}

@DataApi
class Circle(
    override val id: String,
    val radius: Double
) : Shape()

@DataApi
class Rectangle(
    override val id: String,
    val width: Double,
    val height: Double
) : Shape()

/**
 * The serializable counterpart of [Shape], exercising a `@DataApi` leaf of a polymorphic
 * `@Serializable` hierarchy, discriminated by `@SerialName`.
 */
@Serializable
sealed class Event {

    abstract val at: Long

}

@Serializable
@SerialName("created")
@DataApi
class Created(
    override val at: Long,
    val what: String
) : Event() {

    companion object

}
