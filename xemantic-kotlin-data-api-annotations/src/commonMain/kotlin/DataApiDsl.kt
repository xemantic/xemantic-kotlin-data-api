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

package com.xemantic.kotlin.data.api

/**
 * The [DslMarker] carried by every `Builder` the compiler plugin synthesizes for a [DataApi] class.
 *
 * It confines each builder DSL block to its own receiver, so that in a nested block an assignment
 * that does not resolve on the inner builder fails to compile instead of silently falling through
 * to the enclosing builder and mutating the outer object.
 *
 * Applied by the compiler plugin; there is no reason to use it directly.
 */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
public annotation class DataApiDsl
