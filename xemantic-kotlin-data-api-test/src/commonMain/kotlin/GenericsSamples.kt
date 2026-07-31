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
 * A sample generic `@DataApi` class — the shape of an API envelope. The generated `Builder` carries
 * its own copies of `T` and `E`, since a nested class cannot refer to the type parameters of the
 * class it is nested in.
 */
@DataApi
class Response<T, E>(
    val data: T,
    val error: E?,
    val status: Int = 200
)

/**
 * A generic `@DataApi` class with a single type parameter used in a nested position, so that the
 * builder property is `List<T>?` rather than `T?`.
 */
@DataApi
class Page<T>(
    val items: List<T>,
    val next: String?
)

/**
 * A generic `@DataApi` class whose type parameter spells out the `Any?` upper bound every unbounded
 * one has anyway. It is the same parameter as [Page]'s, so it must be accepted: the bound the plugin
 * cannot mirror onto the `Builder`'s own type parameters — and therefore rejects — is a *narrower*
 * one.
 */
@DataApi
class Box<T : Any?>(
    val value: T
)
