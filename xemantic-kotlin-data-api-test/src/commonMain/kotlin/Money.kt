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
