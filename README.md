# xemantic-kotlin-data-api

API-friendly data classes for Kotlin

[<img alt="Maven Central Version" src="https://img.shields.io/maven-central/v/com.xemantic.kotlin/xemantic-kotlin-data-api">](https://central.sonatype.com/artifact/com.xemantic.kotlin/xemantic-kotlin-data-api)
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

When data classes cross an API boundary — serialized to JSON, exposed over the
wire, or consumed by other modules — the default Kotlin ergonomics often fall
short. This Kotlin multiplatform library provides building blocks for
API-friendly data classes, so the same models read well both in code and on the
wire.

## Usage

In `build.gradle.kts` add:

```kotlin
dependencies {
    implementation("com.xemantic.kotlin:xemantic-kotlin-data-api:0.1.0")
}
```
