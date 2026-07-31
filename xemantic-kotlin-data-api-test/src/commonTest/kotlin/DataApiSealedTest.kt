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
import kotlinx.serialization.json.Json
import kotlin.test.Test

/**
 * `@DataApi` is not applicable to a sealed class — `build()` has nothing to instantiate — but it is
 * applicable to each concrete leaf of a sealed hierarchy, which is where a polymorphic payload
 * carries its properties. Lowering the leaf's constructor to `private` does not get in the way of
 * the leaf calling its sealed base's constructor.
 */
class DataApiSealedTest {

    @Test
    fun `should build the leaves of a sealed hierarchy through the DSL`() {
        // when
        val shapes: List<Shape> = listOf(
            Circle {
                id = "c"
                radius = 2.0
            },
            Rectangle {
                id = "r"
                width = 1.0
                height = 3.0
            }
        )

        // then: the property inherited from the sealed base is set through the builder like any other
        assert(shapes.map { it.id } == listOf("c", "r"))
        assert((shapes[0] as Circle).radius == 2.0)
        assert((shapes[1] as Rectangle).height == 3.0)
    }

    @Test
    fun `should match a sealed hierarchy of DataApi leaves exhaustively`() {
        // given
        val shape: Shape = Circle {
            id = "c"
            radius = 2.0
        }

        // when: no `else` branch — the hierarchy is still sealed
        val described = when (shape) {
            is Circle -> "circle"
            is Rectangle -> "rectangle"
        }

        // then
        assert(described == "circle")
    }

    @Test
    fun `should copy a leaf of a sealed hierarchy`() {
        // given
        val circle = Circle {
            id = "c"
            radius = 2.0
        }

        // when
        val bigger = circle.copy { radius = 5.0 }

        // then
        bigger should {
            have(id == "c")
            have(radius == 5.0)
        }
    }

    @Test
    fun `should serialize a DataApi leaf polymorphically`() {
        // given
        val event: Event = Created {
            at = 1L
            what = "thing"
        }

        // when
        val json = Json.encodeToString(event)

        // then: discriminated by the base's serializer, built by the leaf's DSL
        assert(json == """{"type":"created","at":1,"what":"thing"}""")
        assert(Json.decodeFromString<Event>(json) == event)
    }

}
