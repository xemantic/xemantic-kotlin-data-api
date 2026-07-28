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

import com.xemantic.kotlin.data.api.DataApiDsl
import com.xemantic.kotlin.test.assert
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test

/**
 * The constructor-privatization half of the `@DataApi` toolchain, asserted through reflection and
 * therefore only runnable on targets that ship `kotlin-reflect` (the JVM). The compiler plugin must
 * have lowered [Person]'s primary constructor to `internal` (FIR). The rest of the toolchain
 * (synthesized `Person.Builder` and companion `invoke`) is exercised in `commonTest`.
 */
class DataApiReflectionTest {

    @Test
    fun `should lower the primary constructor to internal during compilation of DataApi classes`() {
        assert(Person::class.primaryConstructor!!.visibility == KVisibility.INTERNAL)
    }

    @Test
    fun `should lower a secondary constructor to internal as well`() {
        // given: a public secondary constructor would be a construction path bypassing the
        // builder's required-property validation
        val constructors = Session::class.constructors

        // then
        assert(constructors.size == 2)
        assert(constructors.all { it.visibility == KVisibility.INTERNAL })
    }

    @Test
    fun `should keep a non-public property non-public in the generated Builder`() {
        // given
        val properties = Credentials.Builder::class.declaredMemberProperties
            .associate { it.name to it.visibility }

        // then: the builder must not re-expose, as publicly settable, what the author hid
        assert(properties["user"] == KVisibility.PUBLIC)
        assert(properties["token"] == KVisibility.INTERNAL)
        assert(properties["salt"] == KVisibility.PRIVATE)
    }

    @Test
    fun `should mark the generated Builder as a DSL scope`() {
        // given: the marker is what stops a nested DSL block from resolving an unknown assignment
        // against the enclosing builder
        val markers = Address.Builder::class.annotations.map { it.annotationClass }

        // then
        assert(markers.contains(DataApiDsl::class))
    }

    @Test
    fun `should keep the assignment tracking of defaulted properties private to the Builder`() {
        // given: the flags letting build() tell "unset" from "assigned null" are an implementation
        // detail, and must not surface in the DSL or in the module's public API
        val flags = Server.Builder::class.declaredMemberProperties
            .filter { it.name.endsWith("\$isAssigned") }

        // then: one per defaulted property — `host`, the only one without a default, has none
        assert(flags.map { it.name }.toSet() == setOf(
            "port\$isAssigned",
            "protocol\$isAssigned",
            "url\$isAssigned",
            "comment\$isAssigned"
        ))
        assert(flags.all { it.visibility == KVisibility.PRIVATE })
    }

}
