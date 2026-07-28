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
