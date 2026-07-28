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
