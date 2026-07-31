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

package com.xemantic.kotlin.data.api.test

import com.xemantic.kotlin.test.assert
import com.xemantic.kotlin.test.have
import com.xemantic.kotlin.test.should
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The `data` class counterpart of [Blob] — the behavior the plugin has to reproduce. */
private data class ReferenceBlob(val data: ByteArray)

/**
 * Exercises the `@DataApi` toolchain on every target: the compiler plugin must have synthesized a
 * nested `Person.Builder` plus a `Person(block)` factory function (FIR) and generated their bodies
 * (IR), all in this same module. The constructor-privatization half is asserted via reflection in
 * the JVM-only `DataApiReflectionTest`.
 */
class DataApiEndToEndTest {

    @Test
    fun `should build an instance from all properties`() {
        // when
        val person = Person {
            name = "Ada"
            age = 36
        }

        // then
        person should {
            have(name == "Ada")
            have(age == 36)
        }
    }

    @Test
    fun `should be able to use the nested Builder directly`() {
        // when
        val person = Person.Builder().apply {
            name = "Ada"
            age = 36
        }.build()

        // then
        person should {
            have(name == "Ada")
            have(age == 36)
        }
    }

    @Test
    fun `should allow to omit a nullable property in builder DSL`() {
        // when
        val person = Person {
            name = "Grace"
        }

        // then
        person should {
            have(name == "Grace")
            have(age == null)
        }
    }

    @Test
    fun `should fail when missing a required property in builder DSL`() {
        // when
        val exception = assertFailsWith<IllegalArgumentException> {
            Person {
                age = 42
            }
        }

        // then
        exception should {
            have(message == "Cannot build Person: missing required properties [name]")
        }
    }

    @Test
    fun `should report all the missing required properties at once`() {
        // when
        val exception = assertFailsWith<IllegalArgumentException> {
            Address {}
        }

        // then
        exception should {
            have(message == "Cannot build Address: missing required properties [street, city]")
        }
    }

    @Test
    fun `should report required properties in declaration order skipping a nullable one between them`() {
        // when
        val exception = assertFailsWith<IllegalArgumentException> {
            Account {}
        }

        // then: declaration order is preserved and the intervening nullable `nickname` is excluded
        exception should {
            have(message == "Cannot build Account: missing required properties [id, email]")
        }
    }

    @Test
    fun `should nest a DataApi property built via its own DSL`() {
        // when
        val company = Company {
            name = "Xemantic"
            headquarters = Address {
                street = "Main St 1"
                city = "Berlin"
            }
            branch = Address {
                street = "Second St 2"
                city = "London"
            }
        }

        // then
        company should {
            have(name == "Xemantic")
            headquarters should {
                have(street == "Main St 1")
                have(city == "Berlin")
            }
            branch!! should {
                have(street == "Second St 2")
                have(city == "London")
            }
        }
    }

    @Test
    fun `should allow a nullable nested DataApi property to be omitted`() {
        // when
        val company = Company {
            name = "Xemantic"
            headquarters = Address {
                street = "Main St 1"
                city = "Berlin"
            }
        }

        // then
        assert(company.branch == null)
    }

    @Test
    fun `should reject a missing required nested DataApi property`() {
        // when
        val exception = assertFailsWith<IllegalArgumentException> {
            Company {
                name = "Xemantic"
            }
        }

        // then: only the unset, non-nullable property is reported — the set `name` and the
        // nullable `branch` are excluded
        exception should {
            have(message == "Cannot build Company: missing required properties [headquarters]")
        }
    }

    @Test
    fun `should apply constructor defaults to properties omitted in builder DSL`() {
        // when
        val server = Server {
            host = "xemantic.com"
        }

        // then
        server should {
            have(host == "xemantic.com")
            have(port == 8080)
            have(protocol == "https")
            have(url == "https://xemantic.com:8080")
            have(comment == null)
        }
    }

    @Test
    fun `should override constructor defaults with properties set in builder DSL`() {
        // when
        val server = Server {
            host = "xemantic.com"
            port = 443
            protocol = "http"
            url = "http://xemantic.com"
            comment = "an override of every default"
        }

        // then
        server should {
            have(host == "xemantic.com")
            have(port == 443)
            have(protocol == "http")
            have(url == "http://xemantic.com")
            have(comment == "an override of every default")
        }
    }

    @Test
    fun `should evaluate a default expression against the preceding properties`() {
        // when: `url` defaults to "$protocol://$host:$port", so it must observe the overridden port
        val server = Server {
            host = "xemantic.com"
            port = 443
        }

        // then
        server should {
            have(port == 443)
            have(url == "https://xemantic.com:443")
        }
    }

    @Test
    fun `should not report a defaulted property as missing`() {
        // when
        val exception = assertFailsWith<IllegalArgumentException> {
            Server {}
        }

        // then: only `host`, the sole property without a default, is required
        exception should {
            have(message == "Cannot build Server: missing required properties [host]")
        }
    }

    @Test
    fun `should keep an explicit null assigned to a defaulted nullable property`() {
        // when: the DSL analogue of passing null to a defaulted constructor parameter, which keeps
        // the null — a default applies only to an argument that is *omitted*
        val server = Server {
            host = "xemantic.com"
            protocol = null
        }

        // then: the builder distinguishes "assigned null" from "left unset"
        assert(server.protocol == null)
        // and the default is still what an omitted property resolves to
        assert(Server { host = "xemantic.com" }.protocol == "https")
    }

    @Test
    fun `should report a defaulted non-nullable property explicitly set to null as missing`() {
        // when: a default cannot rescue an explicit null on a non-nullable property
        val exception = assertFailsWith<IllegalArgumentException> {
            Server {
                host = "xemantic.com"
                port = null
            }
        }

        // then
        exception should {
            have(message == "Cannot build Server: missing required properties [port]")
        }
    }

    @Test
    fun `should copy clearing a defaulted nullable property with null`() {
        // given
        val server = Server {
            host = "xemantic.com"
        }

        // when: copy() re-assigns every property, so the cleared one is carried over as null
        // rather than falling back to the default again
        val anonymous = server.copy {
            protocol = null
        }

        // then
        anonymous should {
            have(host == "xemantic.com")
            have(protocol == null)
            have(url == "https://xemantic.com:8080")
        }
    }

    @Test
    fun `should carry defaulted properties over when copying`() {
        // given
        val server = Server {
            host = "xemantic.com"
        }

        // when: `url` is carried over as built, not recomputed from the overridden port
        val secure = server.copy {
            port = 443
        }

        // then
        secure should {
            have(host == "xemantic.com")
            have(port == 443)
            have(protocol == "https")
            have(url == "https://xemantic.com:8080")
        }
    }

    @Test
    fun `should be equal to an instance with the same properties`() {
        // given
        val person = Person {
            name = "Ada"
            age = 36
        }

        // when
        val same = Person {
            name = "Ada"
            age = 36
        }

        // then
        assert(person == same)
    }

    @Test
    fun `should not be equal to an instance with different properties`() {
        // given
        val ada = Person {
            name = "Ada"
            age = 36
        }

        // when
        val grace = Person {
            name = "Grace"
            age = 36
        }

        // then
        assert(ada != grace)
    }

    @Test
    fun `should have the same hashCode as an instance with the same properties`() {
        // given
        val person = Person {
            name = "Ada"
            age = 36
        }

        // when
        val same = Person {
            name = "Ada"
            age = 36
        }

        // then
        assert(person.hashCode() == same.hashCode())
    }

    @Test
    fun `should render toString the way a data class does`() {
        // given
        val person = Person {
            name = "Ada"
            age = 36
        }

        // when
        val string = person.toString()

        // then
        assert(string == "Person(name=Ada, age=36)")
    }

    @Test
    fun `should keep a user-defined toString instead of generating one`() {
        // given
        val tag = Tag {
            name = "kotlin"
        }

        // when
        val string = tag.toString()

        // then
        assert(string == "#kotlin")
    }

    @Test
    fun `should copy overriding a single property`() {
        // given
        val ada = Person {
            name = "Ada"
            age = 36
        }

        // when
        val older = ada.copy {
            age = 37
        }

        // then: the overridden property changes, the rest are carried over from the original
        older should {
            have(name == "Ada")
            have(age == 37)
        }
    }

    @Test
    fun `should copy overriding multiple properties`() {
        // given
        val ada = Person {
            name = "Ada"
            age = 36
        }

        // when
        val grace = ada.copy {
            name = "Grace"
            age = 45
        }

        // then
        grace should {
            have(name == "Grace")
            have(age == 45)
        }
    }

    @Test
    fun `should produce an equal instance when copying with an empty block`() {
        // given
        val ada = Person {
            name = "Ada"
            age = 36
        }

        // when
        val same = ada.copy {}

        // then: an unchanged copy equals the original, the way a data class copy does
        assert(same == ada)
    }

    @Test
    fun `should copy clearing a nullable property with null`() {
        // given
        val ada = Person {
            name = "Ada"
            age = 36
        }

        // when: a nullable property carried over from the original can be reset to null
        val ageless = ada.copy {
            age = null
        }

        // then
        ageless should {
            have(name == "Ada")
            have(age == null)
        }
    }

    @Test
    fun `should leave the original instance unchanged after copying`() {
        // given
        val ada = Person {
            name = "Ada"
            age = 36
        }

        // when
        ada.copy {
            name = "Grace"
            age = 45
        }

        // then: copy() builds a new instance and never mutates the receiver
        ada should {
            have(name == "Ada")
            have(age == 36)
        }
    }

    @Test
    fun `should copy a nested DataApi property while carrying the rest over`() {
        // given
        val company = Company {
            name = "Xemantic"
            headquarters = Address {
                street = "Main St 1"
                city = "Berlin"
            }
        }

        // when: only headquarters is overridden; name and the omitted nullable branch are preserved
        val relocated = company.copy {
            headquarters = Address {
                street = "Second St 2"
                city = "London"
            }
        }

        // then
        relocated should {
            have(name == "Xemantic")
            headquarters should {
                have(street == "Second St 2")
                have(city == "London")
            }
            have(branch == null)
        }
    }

    @Test
    fun `should fail when a copy block clears a required property`() {
        // given
        val ada = Person {
            name = "Ada"
            age = 36
        }

        // when: clearing a required property routes through the same build() validation
        val exception = assertFailsWith<IllegalArgumentException> {
            ada.copy {
                name = null
            }
        }

        // then
        exception should {
            have(message == "Cannot build Person: missing required properties [name]")
        }
    }

    @Test
    fun `should generate equals alongside a user-declared typed equals overload`() {
        // given: `fun equals(other: Money)` overloads, rather than overrides, `Any equals`, so
        // the plugin must still generate the real one
        val five = Money { cents = 5 }

        // when
        val same = Money { cents = 5 }

        // then
        assert(five == same)
        assert(five.hashCode() == same.hashCode())
    }

    @Test
    fun `should leave a user-declared equals overriding through a type alias in place`() {
        // given: `override fun equals(other: Anything?)` overrides `Any equals` through an alias,
        // so the plugin must step aside — generating its own would clash with it
        val one = Alias { value = "a" }

        // when
        val same = Alias { value = "a" }

        // then
        assert(one == same)
        assert(one != Alias { value = "b" })
    }

    @Test
    fun `should hash an array property the way a data class does`() {
        // given: two distinct array instances holding the same bytes
        val bytes = byteArrayOf(1, 2, 3)
        val equalBytes = byteArrayOf(1, 2, 3)

        // when
        val hashedAlike = Blob { data = bytes }.hashCode() == Blob { data = equalBytes }.hashCode()

        // then: whether equal content hashes alike is platform-specific (the JVM hashes an array
        // member by content, JS by identity) — what matters is that the plugin agrees with the
        // `data` class of the same shape on the same platform
        assert(
            hashedAlike ==
                (ReferenceBlob(bytes).hashCode() == ReferenceBlob(equalBytes).hashCode())
        )
    }

    @Test
    fun `should render an array property the way a data class does`() {
        // given
        val bytes = byteArrayOf(1, 2, 3)

        // when
        val rendered = Blob { data = bytes }.toString().removePrefix("Blob")

        // then
        assert(rendered == ReferenceBlob(bytes).toString().removePrefix("ReferenceBlob"))
    }

    @Test
    fun `should build a class with non-public properties`() {
        // when: `token` is internal, so this module can set it; `salt` is private and defaults
        val credentials = Credentials {
            user = "ada"
            token = "s3cret"
        }

        // then
        credentials should {
            have(user == "ada")
            have(token == "s3cret")
            have(saltOrNull() == "pepper")
        }
    }

    @Test
    fun `should carry a private property over when copying`() {
        // given
        val credentials = Credentials {
            user = "ada"
            token = "s3cret"
        }

        // when: nothing outside the builder can assign `salt`, so copy must carry it over itself
        val rotated = credentials.copy {
            token = "rotated"
        }

        // then
        rotated should {
            have(user == "ada")
            have(token == "rotated")
            have(saltOrNull() == "pepper")
        }
    }

}
