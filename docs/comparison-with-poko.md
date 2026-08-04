# Comparison with Poko

[Poko](https://github.com/drewhamilton/Poko) and `xemantic-kotlin-data-api` start from the same
diagnosis — a Kotlin `data class` makes a poor public API — and cut it at different joints.
Poko generates the value semantics and leaves construction alone;
`@DataApi` replaces construction and brings the value semantics along.
Which one fits depends on what your classes are for.

## The shared diagnosis

A `data class` publishes more than its properties:

- its **primary constructor** is public API, so the *order* of its parameters becomes a
  compatibility constraint,
- **`copy`** republishes that positional signature a second time, defaults included, so it breaks
  in all the same ways,
- **`componentN`** functions make destructuring positional by definition.

Both plugins refuse to generate `componentN`, for the same reason:
destructuring would put property order right back into the API.
From there the prescriptions diverge.

## Poko: generate the functions, keep the constructor

With `@Poko`, a class stays a plain class and its constructor stays exactly as declared —
public, positional, checked at compile time.
The plugin generates `equals`, `hashCode` and `toString`, and nothing else:
`copy` is deliberately left out, because no `copy` can be made safe while a positional
constructor is the construction path.
Around that core, Poko offers knobs this library does not:
subsets of the three functions (`@Poko.EqualsAndHashCode`, `@Poko.ToString`),
excluding a property from all of them (`@Poko.Skip`),
content-based semantics for array properties (`@Poko.ReadArrayContent`),
and replacing the annotation with one of your own.

Evolution remains the author's discipline.
A new property is appended with a default value — never inserted — and even appending is only
*source*-compatible: the JVM constructor signature changes, so binary compatibility for
already-compiled callers is still the author's problem.
The constructor is still the API; Poko just relieves it of the `data` keyword's baggage.

## `@DataApi`: replace construction

`@DataApi` lowers every constructor to `private` and generates the builder, the factory
function, and `copy(block)` — plus the same three functions Poko generates.
Because no caller holds a positional constructor call, parameter order stops being API,
and `copy` — the member Poko had to drop — comes back safely in block form.

The cost is the one stated in [Required properties](../README.md#required-properties):
a property added as *required* no longer breaks callers at compile time; it fails at `build()`
at runtime.
Poko keeps that compile-time guarantee precisely by keeping the constructor public —
which is also what keeps the constructor from evolving.
Neither plugin escapes the trade; they pick opposite ends of it.

## Choosing

**Small, stable value types** — a coordinate, a color, a key — favor Poko:
construction stays compile-time-checked, and a builder adds nothing that named constructor
arguments don't already give you.

**Wide, mostly-optional, growing payload models** — the shape of an API SDK's request and
response types — favor `@DataApi`:
the model can gain properties for years without any caller noticing, and the DSL reads like
the payload it describes.

## Side by side

| | Poko | `@DataApi` |
| --- | --- | --- |
| construction path | the declared constructor, public as written | generated builder DSL; every constructor lowered to `private` |
| `equals` / `hashCode` / `toString` | generated, individually selectable | generated as a bundle |
| `copy` | not generated | generated as `copy(block)`, routed through validation |
| destructuring (`componentN`) | not generated | not generated |
| adding a property | append-only, with a default; binary-breaking for compiled JVM callers | invisible to DSL callers, anywhere in the signature |
| required properties | enforced by the compiler at every call site | validated at `build()`, all missing ones reported at once |
| distinctive knobs | custom annotation, `@Poko.Skip`, `@Poko.ReadArrayContent` | derived defaults, unset-vs-`null`, visibility mirroring, generic builders, sealed-leaf factories |
| kotlinx.serialization | orthogonal | composes by generating no companion object |
| IDE (K2) | resolves, with generation hints and checkers, behind a registry flag; `firIdeMode` to dial back | resolves the whole generated DSL behind the same registry flag; `firIdeMode` to dial back |
| targets and license | all Kotlin Multiplatform targets, Apache-2.0 | all Kotlin Multiplatform targets, Apache-2.0 |

## Maturity

Poko has been maintained since 2020 — originally under the name *ExtraCare* — and tracks every
Kotlin compiler release with an explicit compatibility table reaching from Kotlin 1.3 to 2.4.
The IDE story is now much the same for both:
with the `kotlin.k2.only.bundled.compiler.plugins.enabled` registry option disabled, K2 IntelliJ
runs either plugin, and both expose a `firIdeMode` to turn their FIR extensions back down when it
cannot ([details](../README.md#ide-support)).
What Poko still has and this library does not is the record of having followed the compiler across
seven years of releases — which is precisely the risk that mode exists to absorb.
If you need a battle-tested plugin today and can live within a public constructor,
Poko is the safer choice.

## A historical footnote

Poko's original annotation, in its ExtraCare days, was `dev.drewhamilton.extracare.DataApi`.
And in [drewhamilton/Poko#29](https://github.com/drewhamilton/Poko/issues/29) — February 2021,
explaining why that annotation would never grow parameters like `generateBuilder = true` —
its author reasoned that such features would belong in "separate annotations or a separate
plugin" instead.

This library — `@DataApi`, builder generation and all — is in effect that separate plugin,
five years later.
