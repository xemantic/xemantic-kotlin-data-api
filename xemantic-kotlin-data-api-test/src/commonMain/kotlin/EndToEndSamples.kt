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

package com.xemantic.kotlin.data.api.test

import com.xemantic.kotlin.data.api.DataApi

/**
 * A sample `@DataApi` class for the end-to-end test. The compiler plugin lowers the primary
 * constructor to `private` and synthesizes a nested `Person.Builder` plus a top-level
 * `Person(block)` factory function into this module.
 */
@DataApi
class Person(
    val name: String,
    val age: Int?
)

/**
 * A sample `@DataApi` class with two required properties (`id`, `email`) separated by a nullable
 * one (`nickname`), used to assert that `build()` reports missing required properties in
 * constructor-declaration order while skipping the intervening nullable property.
 */
@DataApi
class Account(
    val id: String,
    val nickname: String?,
    val email: String
)

/**
 * A sample `@DataApi` class covering every shape a primary-constructor default value can take: a
 * required property without a default ([host]), a non-nullable property with a default ([port]), a
 * nullable property with a non-null default ([protocol]), a default expression referring to
 * preceding properties ([url]), and the ubiquitous nullable-defaulting-to-null ([comment]).
 */
@DataApi
class Server(
    val host: String,
    val port: Int = 8080,
    val protocol: String? = "https",
    val url: String = "$protocol://$host:$port",
    val comment: String? = null
)

/**
 * A sample `@DataApi` class with non-public primary constructor properties, used to assert that
 * the generated `Builder` re-exposes each one at the *declared* visibility rather than turning it
 * into a publicly settable property — which would let any caller assign what the author hid.
 *
 * [salt] is private, so only the builder's own `build` can read it and only the generated `copy`
 * can carry it over; nothing in the DSL can assign it.
 */
@DataApi
class Credentials(
    val user: String,
    internal val token: String,
    private val salt: String? = "pepper"
) {

    /** Exposes the private [salt] to the test, which cannot see it otherwise. */
    fun saltOrNull(): String? = salt

}

/**
 * A sample `@DataApi` class with a secondary constructor, used to assert that constructor
 * privatization covers *every* constructor: a secondary one left public would hand external
 * callers a construction path bypassing the builder's required-property validation.
 */
@DataApi
class Session(
    val id: String,
    val user: String?
) {

    constructor() : this("anonymous", null)

}

/**
 * A sample `@DataApi` class whose property type is itself a `@DataApi` class ([Address]), used to
 * exercise nested builder DSLs: each `@DataApi` type contributes its own `Builder` and factory
 * function, so an outer builder property can be assigned via the inner type's `{ }` DSL.
 */
@DataApi
class Company(
    val name: String,
    val headquarters: Address,
    val branch: Address?
)

@DataApi
class Address(
    val street: String,
    val city: String
)

/**
 * A sample `@DataApi` class declaring a *typed* `equals` helper, which is an overload rather than
 * an override of `Any.equals`. The plugin must still generate `equals(Any?)`: matching a
 * user-declared `equals` by name and arity alone would leave this class with identity equality.
 */
@DataApi
class Money(
    val cents: Long
) {

    fun equals(other: Money): Boolean = cents == other.cents

}

/**
 * A `@DataApi` class that declares its own `toString`, used to assert that the compiler plugin
 * leaves a user-defined `equals`/`hashCode`/`toString` in place rather than generating a clashing
 * one (the same rule the Kotlin compiler applies to `data` classes).
 */
@DataApi
class Tag(
    val name: String
) {

    override fun toString(): String = "#$name"

}

/** An alias for `Any`, which [Alias] overrides `equals` through. */
typealias Anything = Any

/**
 * A `@DataApi` class overriding `equals` through a type *alias* for `Any`. It is the same override
 * as [Tag]'s `toString`, so the plugin must step aside for it just the same — recognising it by the
 * `override` modifier, which is the only way to declare an `equals` that would clash, rather than by
 * the parameter type, whose spelling here is not `Any` at all.
 */
@DataApi
class Alias(
    val value: String
) {

    override fun equals(other: Anything?): Boolean = other is Alias && other.value == value

    override fun hashCode(): Int = value.hashCode()

}

/**
 * A `@DataApi` class whose name is also the name of one of the members the plugin generates — legal
 * Kotlin, if unidiomatic. Its factory function is named after it and so collides by name with the
 * `copy` generated into every `@DataApi` class; only *where* each was generated tells them apart.
 */
@Suppress("ClassName")
@DataApi
class copy(
    val x: Int
)

/**
 * A sample `@DataApi` class with an array property, whose `hashCode` and `toString` must hash and
 * render the array by *content* — the way a `data` class does — rather than by identity, which is
 * what the array's own inherited `hashCode` would give.
 */
@DataApi
class Blob(
    val data: ByteArray
)
