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

package com.xemantic.kotlin.data.api.compiler

import com.xemantic.kotlin.test.assert
import kotlin.test.Test

/**
 * The other edge of [DataApiDiagnosticsTest]: shapes that sit close to a rejected one and must stay
 * *accepted*.
 *
 * A shape rule is only as good as its boundary, and every rule here reads a declaration through its
 * unresolved syntax — which is all that is available at the phase the generator runs — so each is one
 * careless widening away from rejecting legitimate code. These are the cases that widening would take
 * with it. `xemantic-kotlin-data-api-test` proves the generated code *works*; these prove the plugin
 * lets it be written in the first place.
 */
class DataApiApplicabilityTest {

    @Test
    fun `should accept a type parameter spelling out the default upper bound`() {
        // given: `T : Any?` is the bound an unbounded parameter has anyway, so there is nothing to
        // mirror onto the builder and nothing to reject
        // when
        val result = compile("@DataApi class Box<T : Any?>(val value: T)")

        // then
        assert(result.succeeded)
    }

    @Test
    fun `should accept a private property with a default value`() {
        // given: nothing can assign it through the builder, but the constructor has a value to fall
        // back on, which is what makes it constructible
        // when
        val result = compile("""@DataApi class Token(val user: String, private val salt: String = "pepper")""")

        // then
        assert(result.succeeded)
    }

    @Test
    fun `should accept an internal property without a default value`() {
        // given: unlike a private one, an internal property stays assignable from inside the module
        // when
        val result = compile("@DataApi class Token(val user: String, internal val secret: String)")

        // then
        assert(result.succeeded)
    }

    @Test
    fun `should accept a typed equals overload alongside the generated one`() {
        // given: `fun equals(other: Money)` overloads rather than overrides `Any equals`, so the
        // plugin must still generate the real one rather than mistake this for it
        // when
        val result = compile(
            """
            @DataApi class Money(val cents: Long) {
                fun equals(other: Money): Boolean = cents == other.cents
            }
            """
        )

        // then
        assert(result.succeeded)
    }

    @Test
    fun `should accept an equals overriding through a type alias for Any`() {
        // given: the parameter type is not spelled `Any` at all, so only the `override` modifier
        // identifies this as the member the plugin would clash with
        // when
        val result = compile(
            """
            typealias Anything = Any

            @DataApi class Money(val cents: Long) {
                override fun equals(other: Anything?): Boolean = other is Money && other.cents == cents
                override fun hashCode(): Int = cents.hashCode()
            }
            """
        )

        // then
        assert(result.succeeded)
    }

    @Test
    fun `should accept a class nested in a companion object`() {
        // given: a companion cannot have a companion of its own, so it hosts the factory directly
        // when
        val result = compile(
            """
            class Outer {
                companion object {
                    @DataApi class Inner(val a: String)
                }
            }

            val inner = Outer.Inner { a = "a" }
            """
        )

        // then
        assert(result.succeeded)
    }

    @Test
    fun `should accept a class nested in an object`() {
        // when
        val result = compile(
            """
            object Registry {
                @DataApi class Entry(val key: String)
            }

            val entry = Registry.Entry { key = "k" }
            """
        )

        // then
        assert(result.succeeded)
    }

    @Test
    fun `should accept a same-named convenience overload beside the generated factory`() {
        // given: the generated factory takes a builder block, so a differently-shaped function of
        // the same name is an overload and not a clash — rejecting it by name, the only test
        // available before types resolve, would reject perfectly good code
        // when
        val result = compile(
            """
            class Csv {
                @DataApi class Row(val cells: List<String>)
                companion object {
                    fun Row(line: String): Row = Row { cells = line.split(",") }
                }
            }
            """
        )

        // then
        assert(result.succeeded)
    }

    @Test
    fun `should accept a class named after a generated member`() {
        // given: a lower-case class name collides with the `copy` generated into every `@DataApi`
        // class, so only where each was generated tells the two apart
        // when
        val result = compile(
            """
            @Suppress("ClassName")
            @DataApi class copy(val x: Int)

            val one = copy { x = 1 }
            val two = one.copy { x = 2 }
            """
        )

        // then
        assert(result.succeeded)
    }

    @Test
    fun `should accept a class declaring a secondary constructor`() {
        // when
        val result = compile(
            """
            @DataApi class Session(val id: String, val user: String?) {
                constructor() : this("anonymous", null)
            }
            """
        )

        // then
        assert(result.succeeded)
    }

}
