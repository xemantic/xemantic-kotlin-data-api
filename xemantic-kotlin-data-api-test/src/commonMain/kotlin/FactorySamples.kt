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
 * A sample `@DataApi` class that is also `@Serializable` — the combination a payload model actually
 * crossing an API boundary has, and one that needs no boilerplate: the DSL entry point is a
 * top-level `Point(block)` function, so the companion object kotlinx.serialization generates for
 * `serializer()` is the only one, and nothing has to be declared by hand.
 */
@Serializable
@DataApi
class Point(
    val x: Int,
    val y: Int?
)

/**
 * A top-level `@DataApi` class declaring a companion object of its own, which the factory function —
 * being top-level itself — leaves entirely alone.
 */
@DataApi
class Temperature(
    val celsius: Double
) {

    companion object {

        const val ABSOLUTE_ZERO: Double = -273.15

    }

}

/**
 * An `internal` `@DataApi` class, whose factory function has to be `internal` too. Left at the
 * default the generated factory would be public — a public entry point to a type deliberately kept
 * out of the module's API, and an unexpected entry in the ABI dump.
 */
@DataApi
internal class Ticket(
    val id: String
)

/**
 * The shape an API payload model actually takes: a `@Serializable` polymorphic base whose variants
 * are `@DataApi` classes *nested* inside it. Each variant's DSL entry point is a function named
 * after it in `Payload`'s companion object, so `Payload.Base64 { … }` reads exactly as if it were
 * the constructor, and neither the base nor the variants have to declare a companion by hand.
 */
@Serializable
sealed class Payload {

    abstract val mediaType: String

    @Serializable
    @SerialName("base64")
    @DataApi
    class Base64(
        override val mediaType: String,
        val data: String
    ) : Payload()

    @Serializable
    @SerialName("url")
    @DataApi
    class Url(
        override val mediaType: String,
        val url: String,
        val title: String? = null
    ) : Payload()

    // `Payload` is `@Serializable`, so kotlinx.serialization contributes `serializer()` to its
    // companion; declaring the companion here is what lets `@DataApi` generate the variants'
    // factory functions into the same object rather than a second, clashing one
    companion object

}

/**
 * A `@DataApi` class nested in an `object`, which cannot have a companion object — the factory is
 * generated as a member of the object itself, so `Registry.Entry { … }` still resolves.
 */
object Registry {

    @DataApi
    class Entry(
        val key: String,
        val value: String?
    )

}

/**
 * The third place a nested `@DataApi` class can sit: inside a *companion* object, which is an object
 * that cannot have a companion of its own either. Its factory therefore goes into that companion
 * directly, alongside the factories of the classes nested in [Catalog] itself — and both resolve
 * through the enclosing class, so `Catalog.Item { … }` and `Catalog.Sku { … }` read the same.
 */
class Catalog {

    @DataApi
    class Item(
        val name: String
    )

    companion object {

        @DataApi
        class Sku(
            val code: String
        )

    }

}
