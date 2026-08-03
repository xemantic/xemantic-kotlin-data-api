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

@file:OptIn(ExperimentalCompilerApi::class, CompilerConfiguration.Internals::class)

package com.xemantic.kotlin.data.api.compiler

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * `firIdeMode` selects what the plugin does in a **non-CLI** session, so the property that matters
 * most is the one about the sessions it must *not* touch: whatever it is set to, a CLI compilation
 * still generates everything and still privatizes the constructors.
 *
 * That invariant is what makes the option safe to hand to a consumer. It is an escape hatch for an
 * IDE analyzer that cannot load this plugin — turning it down must never quietly change the
 * artifact the build produces, or a project that set it would ship different bytecode than one that
 * did not.
 */
class DataApiFirIdeModeTest {

    @Test
    fun `should still generate the DSL under the CLI when firIdeMode is none`() {
        // when
        val result = compile(
            """
            @DataApi
            class Person(val name: String, val age: Int? = null)

            fun build(): String = Person { name = "Ada" }.name
            """,
            firIdeMode = DataApiFirIdeMode.NONE
        )

        // then
        result should {
            have(errors.isEmpty())
            have(succeeded)
        }
    }

    @Test
    fun `should still privatize the constructor under the CLI when firIdeMode is none`() {
        // when
        val result = compile(
            """
            @DataApi
            class Person(val name: String)

            fun build(): Person = Person("Ada")
            """,
            firIdeMode = DataApiFirIdeMode.NONE
        )

        // then
        result should {
            have(
                error == "Cannot access 'constructor(name: String): Person': " +
                    "it is private in 'Person'."
            )
        }
    }

    @Test
    fun `should still report a violation under the CLI when firIdeMode is none`() {
        // when
        val result = compile(
            "@DataApi interface Shape",
            firIdeMode = DataApiFirIdeMode.NONE
        )

        // then
        result should {
            have(
                error == "'@DataApi' is applicable only to a class, " +
                    "but 'Shape' is declared as 'interface'"
            )
        }
    }

    @Test
    fun `should default to running everything when no firIdeMode is configured`() {
        // given
        val configuration = CompilerConfiguration()

        // then
        assert(configuration.get(FIR_IDE_MODE_KEY, DataApiFirIdeMode.DEFAULT) == DataApiFirIdeMode.ALL)
    }

    @Test
    fun `should read every firIdeMode value the Gradle plugin can send`() {
        DataApiFirIdeMode.entries.forEach { mode ->
            // given
            val configuration = CompilerConfiguration()

            // when
            DataApiCommandLineProcessor().processOption(
                option = firIdeModeOption(),
                value = mode.name.lowercase(),
                configuration = configuration
            )

            // then
            assert(configuration.get(FIR_IDE_MODE_KEY) == mode)
        }
    }

    @Test
    fun `should reject an unknown firIdeMode value`() {
        // given
        val configuration = CompilerConfiguration()

        // then
        assertFailsWith<CliOptionProcessingException> {
            // when
            DataApiCommandLineProcessor().processOption(
                option = firIdeModeOption(),
                value = "sometimes",
                configuration = configuration
            )
        } should {
            have(
                message == "Unknown 'firIdeMode' value: 'sometimes', " +
                    "expected one of: all|checkers_only|none"
            )
        }
    }

}

/**
 * The plugin's single declared option, taken from the processor rather than rebuilt here, so a
 * renamed option fails these tests instead of silently going unexercised.
 */
@OptIn(ExperimentalCompilerApi::class)
private fun firIdeModeOption() = DataApiCommandLineProcessor().pluginOptions.single()
