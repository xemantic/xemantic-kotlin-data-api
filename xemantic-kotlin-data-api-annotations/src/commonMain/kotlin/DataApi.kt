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
 * Kotlin's backward compatibility guidelines advise library authors to
 * [avoid using data classes in an API](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api),
 * because a `data` class publishes its primary constructor and its generated `copy` as binary API:
 * adding a property changes both signatures — even when the property has a default value — and the
 * generated `componentN` functions make the property order behaviorally significant. This annotation
 * is the workaround the guidelines describe, generated rather than hand-written: the constructor is
 * hidden, `copy` takes a builder block instead of positional arguments, no `componentN` is generated,
 * and adding a property is a purely additive change to the generated builder.
 *
 * The accompanying compiler plugin lowers the constructors to `private`
 * (so instances are created through the generated builder rather than directly), generates
 * a nested `Builder` and a factory function named after the class enabling the DSL form
 * `ClassName { property = value }`, and generates `equals`/`hashCode`/`toString` the way the
 * Kotlin compiler does for `data` classes.
 *
 * The factory function is declared next to a top-level class, and in the enclosing type for a
 * nested one, so `Outer.Inner { … }` keeps reading as the constructor it replaces. The annotated
 * class needs no companion object of its own, which is what lets it be `@Serializable` without any
 * boilerplate.
 *
 * Applicable to a final, top-level or nested class with a primary constructor whose every parameter
 * declares a `val`/`var` property and none of which is a `vararg`, and which does not declare a
 * nested `Builder` of its own. The class may be generic, as long as its type parameters are
 * invariant and unbounded. Any other class is rejected at compile time with an explanation.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class DataApi
