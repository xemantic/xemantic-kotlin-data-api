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

package com.xemantic.kotlin.data.api

/**
 * Marks a class as an API-friendly data class.
 *
 * The accompanying compiler plugin lowers the visibility of the constructors
 * (so instances are created through the generated builder rather than directly), generates
 * a nested `Builder` and a companion `invoke` operator enabling the DSL form
 * `ClassName { property = value }`, and generates `equals`/`hashCode`/`toString` the way the
 * Kotlin compiler does for `data` classes.
 *
 * Applicable to a final, non-generic, top-level or nested class with a primary constructor whose
 * every parameter declares a `val`/`var` property and none of which is a `vararg`, and which
 * declares neither a companion object nor a nested `Builder` of its own — the plugin generates
 * both. Any other class is rejected at compile time with an explanation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class DataApi
