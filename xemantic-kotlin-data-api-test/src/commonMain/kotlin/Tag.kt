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
