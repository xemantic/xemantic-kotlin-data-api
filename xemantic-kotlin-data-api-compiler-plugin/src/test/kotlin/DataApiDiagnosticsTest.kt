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
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import kotlin.test.Test

/**
 * Every class shape `@DataApi` refuses, and the diagnostic it refuses each one with.
 *
 * Each of these would otherwise be a compiler crash, broken bytecode or a class no caller could
 * construct, so the diagnostic is the feature. The assertions are on the *whole* error list, not on
 * "contains", because reporting exactly one error is half of what is being tested: the shape rules
 * have three consumers — the checker, the declaration generator and the status transformer — and a
 * class that any two of them disagree about reports its violation buried under unresolved references
 * and inaccessible constructors.
 */
class DataApiDiagnosticsTest {

    @Test
    fun `should reject a non-class declaration`() {
        // when
        val result = compile("@DataApi interface Shape")

        // then
        result should {
            have(
                error == "'@DataApi' is applicable only to a class, " +
                    "but 'Shape' is declared as 'interface'"
            )
        }
    }

    @Test
    fun `should reject an inner class`() {
        // when
        val result = compile(
            """
            class Outer {
                @DataApi inner class Inner(val a: String)
            }
            """
        )

        // then
        result should {
            have(error == "'@DataApi' is not applicable to an inner class")
        }
    }

    @Test
    fun `should reject a local class`() {
        // when
        val result = compile(
            """
            fun declare() {
                @DataApi class Local(val a: String)
            }
            """
        )

        // then
        result should {
            have(error == "'@DataApi' is not applicable to a local class")
        }
    }

    @Test
    fun `should reject an abstract class`() {
        // when
        val result = compile("@DataApi abstract class Shape(val id: String)")

        // then
        result should {
            have(error == "'@DataApi' is not applicable to an abstract class")
        }
    }

    @Test
    fun `should reject a sealed class`() {
        // when
        val result = compile("@DataApi sealed class Shape(val id: String)")

        // then
        result should {
            have(error == "'@DataApi' is not applicable to a sealed class")
        }
    }

    @Test
    fun `should reject an open class without reporting its subclasses too`() {
        // given: a rejected class keeps its constructors as declared — privatizing a class that has
        // no generated builder to be constructed through would report an inaccessible constructor at
        // every call site and subclass, on top of the one diagnostic explaining what is wrong
        // when
        val result = compile(
            """
            @DataApi open class Shape(val id: String)
            class Circle : Shape("c")
            val direct = Shape("s")
            """
        )

        // then
        result should {
            have(error == "'@DataApi' is not applicable to an open class")
        }
    }

    @Test
    fun `should reject a data class`() {
        // given: both generators produce `equals`/`hashCode`/`toString` and a `copy`, and left to
        // collide they crash Fir2Ir rather than report anything
        // when
        val result = compile("@DataApi data class Money(val cents: Long)")

        // then
        result should {
            have(
                error == "'@DataApi' is not applicable to a 'data' class — it generates 'equals', " +
                    "'hashCode', 'toString' and 'copy' itself; drop the 'data' modifier"
            )
        }
    }

    @Test
    fun `should reject a value class`() {
        // when
        val result = compile("@JvmInline @DataApi value class Cents(val value: Long)")

        // then
        result should {
            have(
                error == "'@DataApi' is not applicable to a 'value' class — it generates 'equals', " +
                    "'hashCode' and 'toString' itself, which a 'value' class generates too"
            )
        }
    }

    @Test
    fun `should reject a variant type parameter`() {
        // given: `var value: T?` in the builder is an `in` position
        // when
        val result = compile("@DataApi class Box<out T>(val value: T)")

        // then
        result should {
            have(
                error == "'@DataApi' does not support a type parameter with declaration-site " +
                    "variance, but 'T' is declared 'out'"
            )
        }
    }

    @Test
    fun `should reject a bounded type parameter`() {
        // given: the builder mirrors the class's type parameters onto its own, but bound providers
        // run before any bound has a resolved type to copy
        // when
        val result = compile("@DataApi class Box<T : CharSequence>(val value: T)")

        // then
        result should {
            have(
                error == "'@DataApi' does not support a type parameter with an upper bound, " +
                    "but 'T' declares one"
            )
        }
    }

    @Test
    fun `should reject a class declaring its own Builder`() {
        // when
        val result = compile(
            """
            @DataApi class Person(val name: String) {
                class Builder
            }
            """
        )

        // then
        result should {
            have(
                error == "'@DataApi' generates a nested 'Builder' class, " +
                    "but 'Person' already declares one"
            )
        }
    }

    @Test
    fun `should reject a class without a primary constructor`() {
        // when
        val result = compile(
            """
            @DataApi class Person {
                constructor(name: String)
            }
            """
        )

        // then
        result should {
            have(
                error == "'@DataApi' requires a primary constructor, " +
                    "which 'Person' does not declare"
            )
        }
    }

    @Test
    fun `should reject a constructor parameter that declares no property`() {
        // when
        val result = compile("@DataApi class Person(name: String)")

        // then
        result should {
            have(
                error == "'@DataApi' requires every primary constructor parameter to declare a " +
                    "property, but 'name' is neither a 'val' nor a 'var'"
            )
        }
    }

    @Test
    fun `should reject a vararg constructor parameter`() {
        // given: a vararg is omissible at the constructor, which the builder cannot express
        // when
        val result = compile("@DataApi class Tags(vararg val names: String)")

        // then
        result should {
            have(
                error == "'@DataApi' does not support a 'vararg' primary constructor parameter, " +
                    "but 'names' is one"
            )
        }
    }

    @Test
    fun `should reject a private property without a default value`() {
        // given: the builder mirrors each property at its declared visibility, so a private one is
        // assignable by nothing — and the constructor is private too, leaving no other way in
        // when
        val result = compile("@DataApi class Token(private val secret: String, val issuer: String)")

        // then
        result should {
            have(
                error == "'@DataApi' requires a 'private' primary constructor property to have a " +
                    "default value, but 'secret' has none — the builder property mirroring it is " +
                    "private too, so nothing outside the builder could ever assign it"
            )
        }
    }

    @Test
    fun `should reject a nested class whose enclosing class declares no companion object`() {
        // given: the factory of a nested class goes into the enclosing class's companion, and the
        // plugin never generates that companion — doing so would add a public `Companion` to a class
        // its author never annotated, and collide with any other plugin contributing one
        // when
        val result = compile(
            """
            class Outer {
                @DataApi class Inner(val a: String)
            }
            """
        )

        // then
        result should {
            have(
                error == "'@DataApi' generates the factory function of a nested class into the " +
                    "enclosing class's companion object, but 'Outer' declares none — add a " +
                    "'companion object' to 'Outer'"
            )
        }
    }

    @Test
    fun `should report the violation of every rejected class`() {
        // given: the checker runs per class, so two bad shapes in one file are two diagnostics
        // when
        val result = compile(
            """
            @DataApi abstract class Shape(val id: String)
            @DataApi data class Money(val cents: Long)
            """
        )

        // then
        assert(result.errors.size == 2)
    }

}
