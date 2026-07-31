# xemantic-kotlin-data-api

API-friendly data classes for Kotlin

[<img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/com.xemantic.kotlin/xemantic-kotlin-data-api-annotations">](https://central.sonatype.com/artifact/com.xemantic.kotlin/xemantic-kotlin-data-api-annotations)
[<img alt="GitHub Release Date" src="https://img.shields.io/github/release-date/xemantic/xemantic-kotlin-data-api">](https://github.com/xemantic/xemantic-kotlin-data-api/releases)
[<img alt="license" src="https://img.shields.io/github/license/xemantic/xemantic-kotlin-data-api?color=blue">](https://github.com/xemantic/xemantic-kotlin-data-api/blob/main/LICENSE)

[<img alt="GitHub Actions Workflow Status" src="https://img.shields.io/github/actions/workflow/status/xemantic/xemantic-kotlin-data-api/build-main.yml">](https://github.com/xemantic/xemantic-kotlin-data-api/actions/workflows/build-main.yml)
[<img alt="GitHub branch check runs" src="https://img.shields.io/github/check-runs/xemantic/xemantic-kotlin-data-api/main">](https://github.com/xemantic/xemantic-kotlin-data-api/actions/workflows/build-main.yml)
[<img alt="GitHub commits since latest release" src="https://img.shields.io/github/commits-since/xemantic/xemantic-kotlin-data-api/latest">](https://github.com/xemantic/xemantic-kotlin-data-api/commits/main/)
[<img alt="GitHub last commit" src="https://img.shields.io/github/last-commit/xemantic/xemantic-kotlin-data-api">](https://github.com/xemantic/xemantic-kotlin-data-api/commits/main/)

[<img alt="GitHub contributors" src="https://img.shields.io/github/contributors/xemantic/xemantic-kotlin-data-api">](https://github.com/xemantic/xemantic-kotlin-data-api/graphs/contributors)
[<img alt="GitHub commit activity" src="https://img.shields.io/github/commit-activity/t/xemantic/xemantic-kotlin-data-api">](https://github.com/xemantic/xemantic-kotlin-data-api/commits/main/)
[<img alt="GitHub code size in bytes" src="https://img.shields.io/github/languages/code-size/xemantic/xemantic-kotlin-data-api">]()
[<img alt="GitHub Created At" src="https://img.shields.io/github/created-at/xemantic/xemantic-kotlin-data-api">](https://github.com/xemantic/xemantic-kotlin-data-api/commits)
[<img alt="kotlin version" src="https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fxemantic%2Fxemantic-kotlin-data-api%2Fmain%2Fgradle%2Flibs.versions.toml&query=versions.kotlin&label=kotlin">](https://kotlinlang.org/docs/releases.html)
[<img alt="discord users online" src="https://img.shields.io/discord/811561179280965673">](https://discord.gg/vQktqqN2Vn)
[![Bluesky](https://img.shields.io/badge/Bluesky-0285FF?logo=bluesky&logoColor=fff)](https://bsky.app/profile/xemantic.com)

## Why?

When data classes cross an API boundary — serialized to JSON, exposed over the wire, or consumed by other modules — the default Kotlin ergonomics fall short:

* **The constructor is the API.** A `data class` publishes its primary constructor, so the *order* of its parameters becomes a compatibility constraint. Inserting a property in the middle is a source- and binary-breaking change for every caller.
* **Wide, mostly optional models read poorly.** API payloads tend to have many properties, most of them optional. Constructing them with named arguments produces a wall of `property = null` noise that looks nothing like the payload it maps to.
* **Nesting does not read structurally.** A nested request object built out of nested constructor calls reads inside-out, while the payload it describes is a tree.

The `@DataApi` annotation, backed by a K2 compiler plugin, addresses this by making the *builder* the public construction path and hiding the constructors:

```kotlin
@DataApi
class Person(
    val name: String,
    val age: Int?
)

val ada = Person {
    name = "Ada"
    age = 36
}
```

`equals`, `hashCode`, `toString` and a `copy` are all still there — but the class stays free to grow, because no caller is holding onto a positional constructor call.
Destructuring is the one `data` class feature deliberately left out: `component1()`, `component2()` … are positional by nature, and generating them would put the property *order* back into the API the plugin just took it out of.

## Usage

Apply the Gradle plugin next to one of the Kotlin plugins (`multiplatform`, `jvm` or `android`):

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.10"
    id("com.xemantic.kotlin.data.api") version "0.1.0"
}
```

The plugin registers the compiler plugin on every compilation and adds the `@DataApi` annotations dependency to the right source set (`commonMainImplementation` for multiplatform, `implementation` for JVM/Android), so no explicit dependency declaration is needed.
If none of the supported Kotlin plugins is applied, the build fails with an explanation instead of leaving you with an unresolved `@DataApi` reference.

Then annotate a class:

```kotlin
import com.xemantic.kotlin.data.api.DataApi

@DataApi
class Person(
    val name: String,
    val age: Int?
)
```

## What the compiler plugin generates

For every `@DataApi` class the plugin:

* **lowers every constructor to `private`** — primary *and* secondary — so that the builder is the only way in,
* **generates a nested `Builder`** holding a `var` per primary-constructor property, plus `build()`,
* **generates a factory function named after the class**, `fun Person(block: Builder.() -> Unit)`, which is what enables the `Person { … }` form,
* **generates `copy(block: Builder.() -> Unit)`**,
* **generates `equals`, `hashCode` and `toString`** exactly the way the compiler generates them for a `data` class,
* **marks the `Builder` with `@DataApiDsl`** — a `@DslMarker` — so a nested DSL block cannot silently assign a property of the enclosing builder.

`private` rather than `internal`, because `internal` is a Kotlin-only boundary: an internal *function* is name-mangled in the bytecode, but a constructor cannot be, so the JVM backend emits it as `ACC_PUBLIC` and Java callers keep the positional constructor — and with it the parameter order — as binary API.
A private constructor is `ACC_PRIVATE`, and `build()` reaches it through the synthetic accessor the compiler generates for a nested class.

Roughly, `Person` above ends up equivalent to hand-writing:

```kotlin
class Person private constructor(
    val name: String,
    val age: Int?
) {

    @DataApiDsl
    class Builder {
        var name: String? = null
        var age: Int? = null
        fun build(): Person { /* validates, then calls the constructor */ }
    }

    fun copy(block: Builder.() -> Unit): Person { /* … */ }

    override fun equals(other: Any?): Boolean { /* … */ }
    override fun hashCode(): Int { /* … */ }
    override fun toString(): String { /* … */ }

}

fun Person(block: Person.Builder.() -> Unit): Person = Person.Builder().apply(block).build()
```

A top-level function — the same idiom as kotlinx's own `Json { … }` — which leaves the class without a generated companion object, and so free to compose with other compiler plugins; see [kotlinx.serialization](#kotlinxserialization) below.
For a **nested** class the factory is generated into the enclosing type instead — its companion object, or the object itself — so `Payload.Base64 { … }` still reads as the constructor it replaces:

```kotlin
sealed class Payload {
    @DataApi
    class Base64(val mediaType: String, val data: String) : Payload()
}

val payload = Payload.Base64 {
    mediaType = "image/png"
    data = "iVBORw0KGgo="
}
```

The builder can also be used directly, which is handy when the properties are assigned conditionally:

```kotlin
val person = Person.Builder().apply {
    name = "Ada"
    age = 36
}.build()
```

### Required properties

Every builder property is nullable, so any of them can be left unset.
A property whose constructor parameter is **non-nullable and has no default** is required, and `build()` reports *all* the missing ones at once, in declaration order:

```kotlin
Person { age = 42 }
// IllegalArgumentException: Cannot build Person: missing required properties [name]
```

Note what this trades away.
With a `data` class, adding a required property breaks every call site at *compile* time, which is precisely why the constructor cannot evolve; here the same addition leaves callers compiling and moves the failure to `build()` at runtime.
That is the point — the class becomes free to grow — but a property added as required is a change consumers discover when they run the code, not when they build it, so grow the model with nullable or defaulted properties wherever the payload allows it.

### Nesting

Because each `@DataApi` type contributes its own DSL, a nested model reads like the payload it describes:

```kotlin
val company = Company {
    name = "Xemantic"
    headquarters = Address {
        street = "Main St 1"
        city = "Berlin"
    }
}
```

### Constructor defaults

Primary-constructor default values are honored by the builder, including defaults referring to preceding properties:

```kotlin
@DataApi
class Server(
    val host: String,
    val port: Int = 8080,
    val protocol: String? = "https",
    val url: String = "$protocol://$host:$port",
    val comment: String? = null
)

Server { host = "xemantic.com" }.url  // "https://xemantic.com:8080"
Server { host = "xemantic.com"; port = 443 }.url  // "https://xemantic.com:443"
```

The builder distinguishes *"left unset"* from *"explicitly assigned `null`"*, so a default is applied only to a property that was never touched — assigning `null` to a nullable property keeps the `null`, and assigning `null` to a non-nullable one is reported as missing.

### Generic classes

A `@DataApi` class may be generic, and the type arguments flow through the whole generated DSL:

```kotlin
@DataApi
class Response<T, E>(
    val data: T,
    val error: E?,
    val status: Int = 200
)

val response = Response {              // inferred as Response<String, Int>
    data = "payload"
    error = 42
}

val empty = Response<String, Int> {    // or stated explicitly
    data = "payload"
}
```

The `Builder` carries its own copies of the class's type parameters, since a nested class cannot refer to the type parameters of the class it is nested in — so the direct form is `Response.Builder<String, Int>()`.

A property typed `T` counts as **required**, even though an unbounded `T` can be instantiated with a nullable type: it is the property the caller is expected to assign, and leaving it unset would hand a null to a parameter that, for `Response<String, …>`, is a non-null `String`.
Declare the property as `T?` for a payload that may legitimately be absent.

Type parameters have to be **invariant and unbounded**.
A covariant `out T` cannot occupy the `var data: T?` position a builder needs, and an upper bound would have to be mirrored onto the builder's own type parameters at a point in compilation where the bound has no resolved type yet.
Both are rejected with a diagnostic rather than silently mis-generated.

### `copy`

`copy` takes a builder block instead of named arguments, and routes through the same validation:

```kotlin
val older = ada.copy { age = 37 }
val ageless = ada.copy { age = null }
ada.copy { name = null }  // IllegalArgumentException: … missing required properties [name]
```

Properties not mentioned in the block — including `private` ones the caller cannot even see — are carried over from the original instance as built, not re-defaulted.

### Visibility

The generated `Builder` re-exposes each property at its *declared* visibility, so a `@DataApi` class does not accidentally publish what its author hid:

```kotlin
@DataApi
class Credentials(
    val user: String,
    internal val token: String,
    private val salt: String? = "pepper"
)
```

`salt` is settable by nobody, readable only by the generated `build`, and carried over by `copy`.

### User-declared members

An `equals`, `hashCode` or `toString` you declare yourself is left in place and nothing clashing is generated — the same rule the Kotlin compiler applies to `data` classes.
A *typed* overload such as `fun equals(other: Money)` does not count, since it overloads rather than overrides `Any.equals`, so the real `equals(Any?)` is still generated.

### Sealed hierarchies

`@DataApi` does not apply to a sealed class itself — `build()` would have nothing to instantiate — but it applies to each concrete leaf, which is where a polymorphic payload carries its properties:

```kotlin
sealed class Shape {
    abstract val id: String
}

@DataApi
class Circle(
    override val id: String,
    val radius: Double
) : Shape()

val shape: Shape = Circle {
    id = "c"
    radius = 2.0
}
```

A property inherited from the sealed base is set through the builder like any other, `when` over the hierarchy stays exhaustive, and lowering the leaf's constructor to `private` does not interfere with it calling its base's constructor.
Leaves nested *inside* the base get their factory function in the base's companion object, so a polymorphic `@Serializable` hierarchy discriminated by `@SerialName` reads as `Payload.Base64 { … }` — see [kotlinx.serialization](#kotlinxserialization) below.

### kotlinx.serialization

A model that crosses an API boundary usually has to serialize, and `@DataApi` composes with `@Serializable` with no boilerplate at all:

```kotlin
@Serializable
@DataApi
class Point(
    val x: Int,
    val y: Int?
)

Json.encodeToString(Point { x = 1; y = 2 })  // {"x":1,"y":2}
Json.decodeFromString<Point>("""{"x":1,"y":2}""")
```

What makes this work is that the plugin generates no companion object.
A companion *generated* by a compiler plugin can be extended by no other plugin, and two plugins each generating one for the same class fails the compilation outright — so the only companion here is the one kotlinx.serialization generates for `serializer()`, and neither plugin needs anything declared by hand.

Deserialization drives the private primary constructor, which the generated serializer reaches as a nested declaration of the class it deserializes, so lowering the constructor costs nothing here.

One case still needs the line: a **nested** `@DataApi` class, whose factory goes into the enclosing class's companion object.
That companion has to be one the enclosing class declares — the plugin never generates it.
Generating it would silently add a public `Companion` to a class you never annotated, part of its binary API from then on, and would collide with any other plugin contributing a companion to the same class, kotlinx.serialization among them.

```kotlin
@Serializable
sealed class Payload {

    @Serializable
    @DataApi
    class Base64(val mediaType: String, val data: String) : Payload()

    companion object   // ← shared ground: `serializer()` and `Base64(block)` both land here

}
```

Forgetting it is a compile-time error naming the fix, not the internal compiler error it would otherwise be.
An enclosing `object` needs nothing: it hosts the factories of the classes nested in it directly, having no companion to delegate to.

## Applicable class shapes

`@DataApi` applies to a **final class** with a **primary constructor** whose every parameter declares a `val`/`var` property.
Anything the generated code could not express is rejected at compile time with a diagnostic explaining why.
In particular the class must not be:

* an interface, object, annotation class, enum or enum entry,
* an `inner`, `local`, `abstract`, `sealed` or `open` class — `abstract` and `sealed` because `build()` has nothing to instantiate (annotate the concrete leaves instead), `open` for the same reason a `data` class is final, that the generated `equals` tests `other is Person` and a subclass would pass it,
* a class declaring a nested `Builder` of its own (the plugin generates one),
* a class with a primary-constructor parameter that is not a property, or is a `vararg`,
* a generic class whose type parameters are variant or bounded,
* a `data` or `value` class — it generates the very members those generate,
* a class with a `private` primary-constructor property that has no default value, which nothing could ever assign,
* a class nested in a class that declares no `companion object` of its own.

## Known issues

The IDE / language-server analyzer does not run this compiler plugin, so code using the synthesized DSL (`Person { … }`, `Person.Builder`, `copy { … }`) is reported as *"Unresolved reference"* even though it compiles and runs.
Verify against the build, not the editor.

## Supported platforms

The annotations are a Kotlin Multiplatform library, and the compiler plugin runs on every backend, covering: JVM, JS, WasmJs, WasmWasi and all the [Kotlin/Native targets](https://kotlinlang.org/docs/native-target-support.html) of tiers 1–3 (Apple, Linux, Windows, Android Native).

## Project structure

| module                                     | purpose                                                                                  |
|--------------------------------------------|------------------------------------------------------------------------------------------|
| `xemantic-kotlin-data-api-annotations`     | the multiplatform `@DataApi` / `@DataApiDsl` annotations — the only artifact on the consumer's compile classpath |
| `xemantic-kotlin-data-api-compiler-plugin` | the K2 compiler plugin: FIR declaration generation, status transformation and checkers, plus IR body generation |
| `xemantic-kotlin-data-api-gradle-plugin`   | wires the compiler plugin and the annotations into a consumer project                    |
| `xemantic-kotlin-data-api-test`            | end-to-end consumer module exercising the toolchain on every target; not published       |

## Building

```shell
./gradlew build
```

After changing public API of a published module, refresh the binary-compatibility dumps:

```shell
./gradlew apiDump
```
