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

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * A generic `@DataApi` class gets the same DSL as a non-generic one, with the type arguments flowing
 * through the generated `Builder`, factory function and `copy` — each of which has to state them in
 * terms of different type parameters (the builder's own copies, the factory's own, and the class's).
 */
class DataApiGenericsTest {

    @Test
    fun `should build a generic class inferring its type arguments`() {
        // when
        val response = Response {
            data = "payload"
            error = 42
        }

        // then
        response should {
            have(data == "payload")
            have(error == 42)
            have(status == 200)
        }
    }

    @Test
    fun `should build a generic class with explicit type arguments`() {
        // when
        val response = Response<String, Int> {
            data = "payload"
        }

        // then
        response should {
            have(data == "payload")
            have(error == null)
        }
    }

    @Test
    fun `should keep a type argument through the nested Builder`() {
        // when
        val page = Page.Builder<String>().apply {
            items = listOf("a", "b")
        }.build()

        // then
        assert(page.items == listOf("a", "b"))
        assert(page.next == null)
    }

    @Test
    fun `should apply a constructor default to a generic class`() {
        // when
        val response = Response {
            data = listOf(1, 2, 3)
            error = "none"
            status = 201
        }

        // then
        response should {
            have(data == listOf(1, 2, 3))
            have(status == 201)
        }
    }

    @Test
    fun `should report a missing required property of a generic class`() {
        // when
        val exception = assertFailsWith<IllegalArgumentException> {
            Response<String, String> { error = "boom" }
        }

        // then
        exception should {
            have(message == "Cannot build Response: missing required properties [data]")
        }
    }

    @Test
    fun `should accept an explicit null for a type parameter instantiated as nullable`() {
        // when
        val response = Response<String?, Int> {
            data = null
            error = 1
        }

        // then
        response should {
            have(data == null)
            have(error == 1)
            have(status == 200)
        }
    }

    @Test
    fun `should report an unassigned type parameter property as missing`() {
        // when
        val exception = assertFailsWith<IllegalArgumentException> {
            Response<String?, Int> { error = 1 }
        }

        // then
        exception should {
            have(message == "Cannot build Response: missing required properties [data]")
        }
    }

    @Test
    fun `should build a class whose type parameter spells out the default upper bound`() {
        // when
        val box = Box { value = "a" }

        // then
        assert(box.value == "a")
    }

    @Test
    fun `should copy a generic class overriding a single property`() {
        // given
        val page = Page {
            items = listOf("a")
            next = "cursor"
        }

        // when
        val copy = page.copy { next = null }

        // then
        copy should {
            have(items == listOf("a"))
            have(next == null)
        }
    }

    @Test
    fun `should implement equals and hashCode for a generic class`() {
        // given
        val one = Page { items = listOf("a") }
        val other = Page { items = listOf("a") }

        // then
        assert(one == other)
        assert(one.hashCode() == other.hashCode())
    }

    @Test
    fun `should render toString for a generic class`() {
        // when
        val page = Page {
            items = listOf("a")
            next = "cursor"
        }

        // then
        assert(page.toString() == "Page(items=[a], next=cursor)")
    }

}
