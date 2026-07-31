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

package com.xemantic.kotlin.data.api.compiler.fir

import com.xemantic.kotlin.data.api.compiler.BUILDER_NAME
import com.xemantic.kotlin.data.api.compiler.DATA_API_ANNOTATION_FQ_NAME
import com.xemantic.kotlin.data.api.compiler.DATA_API_DSL_ANNOTATION_CLASS_ID
import com.xemantic.kotlin.data.api.compiler.DataApiPluginKey
import com.xemantic.kotlin.data.api.compiler.assignedFlagName
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.plugin.createTopLevelFunction
import org.jetbrains.kotlin.fir.plugin.SimpleFunctionBuildingContext
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeAttributes
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.types.toLookupTag
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.CompilerConeAttributes
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructType
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withAttributes
import org.jetbrains.kotlin.fir.types.withNullability
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
 * Synthesizes, for every `@DataApi`-annotated class, a nested `Builder` and a factory function
 * named after the class, e.g. for `Person`:
 *
 * ```
 * class Person {
 *     class Builder {
 *         var name: String? = null
 *         var age: Int? = null
 *         private var age$isAssigned: Boolean = false // only when `age` has a default value
 *         fun build(): Person = ...
 *     }
 *     fun copy(block: Builder.() -> Unit): Person =
 *         Builder().apply { name = this@Person.name; age = this@Person.age }.apply(block).build()
 * }
 *
 * fun Person(block: Person.Builder.() -> Unit): Person = Person.Builder().apply(block).build()
 * ```
 *
 * A function rather than a companion `invoke`, because the annotated class then needs no companion
 * object of its own — one generated by a plugin can be extended by no other plugin, and two plugins
 * generating one for the same class fails the compilation outright, so a `@Serializable` class
 * would otherwise have had to declare a companion by hand. A *nested* class's factory goes into the
 * enclosing type instead — the enclosing object itself, or the companion object the enclosing class
 * declares — keeping `Outer.Inner { … }` readable as the constructor it replaces.
 *
 * Only the declaration *shapes* are produced here (FIR); the bodies of `build` and the factory are
 * filled in by the IR backend (see `DataApiIrGenerationExtension`), which also takes over the
 * setters of properties carrying an `$isAssigned` flag. The builder's mutable `var`s and both
 * constructors get their bodies generated by Fir2Ir automatically.
 */
class DataApiBuilderGenerator(
    session: FirSession
) : FirDeclarationGenerationExtension(session) {

    private companion object {
        val BUILD_NAME: Name = Name.identifier("build")
        val COPY_NAME: Name = Name.identifier("copy")
        val BLOCK_NAME: Name = Name.identifier("block")
        val EQUALS_NAME: Name = Name.identifier("equals")
        val HASHCODE_NAME: Name = Name.identifier("hashCode")
        val TO_STRING_NAME: Name = Name.identifier("toString")
        val OTHER_NAME: Name = Name.identifier("other")
        val DATA_MEMBER_NAMES: Set<Name> = setOf(EQUALS_NAME, HASHCODE_NAME, TO_STRING_NAME)
        val PREDICATE: DeclarationPredicate =
            DeclarationPredicate.create { annotated(DATA_API_ANNOTATION_FQ_NAME) }
        val LOOKUP_PREDICATE: LookupPredicate =
            LookupPredicate.create { annotated(DATA_API_ANNOTATION_FQ_NAME) }
    }

    /** Builder class id -> owning `@DataApi` class symbol. */
    private val builderOwners = mutableMapOf<ClassId, FirRegularClassSymbol>()

    /** Every `@DataApi` class in the module, which the top-level factory functions are derived from. */
    private val dataApiClasses: List<FirRegularClassSymbol> by lazy {
        session.predicateBasedProvider
            .getSymbolsByPredicate(LOOKUP_PREDICATE)
            .filterIsInstance<FirRegularClassSymbol>()
            .filter { it.dataApiViolation(session) == null }
    }

    /**
     * The `@DataApi` classes nested directly in [symbol], whose factory functions are generated into
     * [symbol]'s companion object (or into [symbol] itself when it is an object).
     */
    private fun nestedDataApiClasses(symbol: FirClassSymbol<*>): List<FirRegularClassSymbol> =
        symbol.declaredNestedClasses().filter { isDataApiClass(it) }

    /**
     * Whether [symbol] is a `@DataApi` class the plugin can generate for. A class the annotation
     * does not fit — see [dataApiViolation] — is left entirely untouched, so that the error
     * `DataApiCheckersExtension` reports on it is the only thing the user has to read, rather than
     * the fallout of half-generated declarations.
     */
    private fun isDataApiClass(symbol: FirClassSymbol<*>): Boolean =
        session.predicateBasedProvider.matches(PREDICATE, symbol) &&
            symbol.dataApiViolation(session) == null

    /**
     * The classes whose nested `@DataApi` classes' factory functions belong in [classSymbol].
     *
     * An object hosts the factories of the classes nested directly in it, since `Outer.Inner { … }`
     * resolves against the object's own members. A *companion* object is both: it hosts those of the
     * classes nested in it, having no companion of its own to delegate to, and those of the classes
     * nested in the class it is the companion of, which is what makes `Payload.Base64 { … }` resolve.
     */
    private fun factoryHostOwners(classSymbol: FirClassSymbol<*>): List<FirRegularClassSymbol> {
        if (classSymbol !is FirRegularClassSymbol) return emptyList()
        if (classSymbol.classKind != ClassKind.OBJECT) return emptyList()
        val hosts = mutableListOf(classSymbol)
        if (classSymbol.isCompanion) {
            classSymbol.classId.outerClassId
                ?.let { session.symbolProvider.getClassLikeSymbolByClassId(it) }
                ?.let { it as? FirRegularClassSymbol }
                ?.let { hosts += it }
        }
        return hosts
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> =
        if (isDataApiClass(classSymbol)) setOf(BUILDER_NAME) else emptySet()

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        if (owner !is FirRegularClassSymbol) return null
        if (name != BUILDER_NAME || !isDataApiClass(owner)) return null
        return createNestedClass(owner, BUILDER_NAME, DataApiPluginKey) {
            // a nested class cannot refer to the type parameters of the class it is nested in,
            // so the builder of a generic class carries its own copies of them, and everything
            // the builder generates from the constructor is substituted onto those — see
            // `builderSubstitutor`. Bounds are not copied, which is why `dataApiViolation`
            // rejects a bounded type parameter outright.
            owner.typeParameterSymbols.forEach { parameter ->
                typeParameter(parameter.name, key = DataApiPluginKey)
            }
        }
            // confines the builder DSL block to its own receiver, so that in a nested block an
            // assignment that does not resolve here fails to compile instead of falling
            // through to the enclosing builder and mutating the outer object
            .apply { replaceAnnotations(listOf(dslMarkerAnnotation())) }
            .symbol
            .also { builderOwners[it.classId] = owner }
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        val classId = classSymbol.classId
        if (classId in builderOwners) {
            return dataPropertyNames(builderOwners.getValue(classId))
                .flatMapTo(mutableSetOf()) { (name, tracksAssignment) ->
                    if (tracksAssignment) listOf(name, assignedFlagName(name)) else listOf(name)
                }
                .apply { add(BUILD_NAME); add(SpecialNames.INIT) }
        }
        val names = mutableSetOf<Name>()
        // the factory functions of the `@DataApi` classes this type hosts — `Payload.Base64 { … }`
        // resolves to `Payload.Companion.Base64`
        factoryHostOwners(classSymbol).forEach { host ->
            nestedDataApiClasses(host).mapTo(names) { it.name }
        }
        if (isDataApiClass(classSymbol)) {
            names += (DATA_MEMBER_NAMES - userDeclaredDataMemberNames(classSymbol)) + COPY_NAME
        }
        return names
    }

    /**
     * The factory function of every *top-level* `@DataApi` class — `fun Person(block: …): Person`
     * declared next to the class, the same shape kotlinx.serialization's own `Json { … }` has.
     *
     * A factory rather than a companion `invoke` because a companion object generated by a plugin
     * can be extended by no other plugin, and two plugins generating one for the same class fails
     * the compilation outright — so a `@Serializable` `@DataApi` class would have had to declare a
     * companion by hand. A function named after the class keeps the `Person { … }` call syntax
     * without the annotated class needing a companion at all.
     */
    @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
    override fun getTopLevelCallableIds(): Set<CallableId> =
        dataApiClasses
            .filter { !it.classId.isNestedClass }
            .mapTo(mutableSetOf()) { CallableId(it.classId.packageFqName, it.name) }

    /**
     * The `equals`/`hashCode`/`toString` names already declared by the user on [classSymbol], which
     * the plugin must leave untouched instead of generating a clashing member (as the Kotlin
     * compiler does for `data` classes).
     *
     * Recognised by the `override` modifier rather than by the parameter type: overriding is the
     * only way to declare the `equals(Any?)`/`hashCode()`/`toString()` that these generated members
     * would clash with, while an unrelated overload such as `fun equals(other: Money): Boolean` —
     * which does *not* override `Any.equals`, and would leave the class with identity equality if it
     * were mistaken for one — cannot carry it. Reading the modifier also keeps this free of any type
     * access, which the phase this can be called from does not allow (see [dataPropertyNames]);
     * matching the parameter type by name instead would miss a type alias for `Any?` and would
     * falsely match an unrelated user type called `Any`.
     */
    @OptIn(DirectDeclarationsAccess::class)
    private fun userDeclaredDataMemberNames(classSymbol: FirClassSymbol<*>): Set<Name> =
        classSymbol.declarationSymbols
            .filterIsInstance<FirNamedFunctionSymbol>()
            .filterTo(mutableSetOf()) { function ->
                function.rawStatus.isOverride && when (function.name) {
                    EQUALS_NAME -> function.valueParameterSymbols.size == 1
                    HASHCODE_NAME, TO_STRING_NAME -> function.valueParameterSymbols.isEmpty()
                    else -> false
                }
            }
            .mapTo(mutableSetOf()) { it.name }

    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        val owner = context?.owner ?: return emptyList()
        val dataOwner = builderOwners[owner.classId] ?: return emptyList()
        val name = callableId.callableName
        val properties = dataProperties(dataOwner)
        val substitutor = builderSubstitutor(dataOwner, owner)
        properties.firstOrNull { it.name == name }?.let { property ->
            return listOf(
                createMemberProperty(
                    owner = owner,
                    key = DataApiPluginKey,
                    name = property.name,
                    returnType = substitutor.substituteOrSelf(property.builderType),
                    isVal = false,
                    hasBackingField = true
                ) {
                    // a `private`/`internal` property must not become settable from anywhere
                    // through the builder
                    visibility = property.visibility
                }.symbol
            )
        }
        // the private `<property>$isAssigned` flag
        properties.firstOrNull { it.tracksAssignment && assignedFlagName(it.name) == name }
            ?: return emptyList()
        return listOf(
            createMemberProperty(
                owner = owner,
                key = DataApiPluginKey,
                name = name,
                returnType = session.builtinTypes.booleanType.coneType,
                isVal = false,
                hasBackingField = true
            ) {
                visibility = Visibilities.Private
            }.symbol
        )
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        val name = callableId.callableName
        // a top-level factory function has no owning class
        val owner = context?.owner ?: return topLevelFactory(callableId)
        val classId = owner.classId
        val nestedFactoryTarget = factoryHostOwners(owner).firstNotNullOfOrNull { host ->
            nestedDataApiClasses(host).firstOrNull { it.name == name }
        }
        return when {
            classId in builderOwners && name == BUILD_NAME -> {
                // `Builder<T>.build(): Box<T>` — the builder's own type parameters, not the class's
                val targetType = builderOwners.getValue(classId).targetType(owner.ownTypeArguments())
                listOf(
                    createMemberFunction(
                        owner, DataApiPluginKey, BUILD_NAME, targetType
                    ).symbol
                )
            }
            nestedFactoryTarget != null -> listOf(
                // `Payload.Companion.Base64(block): Payload.Base64` — the host is not generic even
                // when the nested class is, so the factory carries its own copies of its type
                // parameters
                createMemberFunction(
                    owner,
                    DataApiPluginKey,
                    name,
                    { typeParameters: List<FirTypeParameter> ->
                        nestedFactoryTarget.targetType(typeParameters.toTypeArguments())
                    }
                ) {
                    factoryShape(nestedFactoryTarget)
                }.symbol
            )
            isDataApiClass(owner) && name == COPY_NAME -> {
                val dataOwner = owner as FirRegularClassSymbol
                // a member of `Box<T>`, so it is typed with the class's own type parameters
                val builderType = builderClassId(dataOwner)
                    .createConeType(session, dataOwner.ownTypeArguments())
                val blockType = builderType.toExtensionFunctionType()
                listOf(
                    createMemberFunction(
                        owner, DataApiPluginKey, COPY_NAME, dataOwner.targetType()
                    ) {
                        valueParameter(BLOCK_NAME, blockType)
                    }.symbol
                )
            }
            isDataApiClass(owner) && name == EQUALS_NAME -> listOf(
                createMemberFunction(
                    owner, DataApiPluginKey, EQUALS_NAME, session.builtinTypes.booleanType.coneType
                ) {
                    status { isOverride = true }
                    valueParameter(OTHER_NAME, session.builtinTypes.nullableAnyType.coneType)
                }.symbol
            )
            isDataApiClass(owner) && name == HASHCODE_NAME -> listOf(
                createMemberFunction(
                    owner, DataApiPluginKey, HASHCODE_NAME, session.builtinTypes.intType.coneType
                ) {
                    status { isOverride = true }
                }.symbol
            )
            isDataApiClass(owner) && name == TO_STRING_NAME -> listOf(
                createMemberFunction(
                    owner, DataApiPluginKey, TO_STRING_NAME, session.builtinTypes.stringType.coneType
                ) {
                    status { isOverride = true }
                }.symbol
            )
            else -> emptyList()
        }
    }

    /** `fun Person(block: Person.Builder.() -> Unit): Person`, declared next to the class. */
    @OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
    private fun topLevelFactory(callableId: CallableId): List<FirNamedFunctionSymbol> {
        val target = dataApiClasses.firstOrNull {
            !it.classId.isNestedClass &&
                it.classId.packageFqName == callableId.packageName &&
                it.name == callableId.callableName
        } ?: return emptyList()
        return listOf(
            createTopLevelFunction(
                DataApiPluginKey,
                callableId,
                { typeParameters: List<FirTypeParameter> ->
                    target.targetType(typeParameters.toTypeArguments())
                }
            ) {
                factoryShape(target)
                // a top-level factory lands in a generated file facade of its own, so `private` —
                // which in Kotlin means private *to a file* — would put it out of reach of the very
                // file declaring the class. `internal` is the narrowest visibility that still keeps
                // it callable from there, and it never widens what the class itself exposes
                if (visibility == Visibilities.Private) visibility = Visibilities.Internal
            }.symbol
        )
    }

    /**
     * The shape shared by both factory forms: copies of [target]'s type parameters, and a single
     * `block` parameter typed as an extension function on [target]'s `Builder`, instantiated with
     * those copies.
     *
     * The factory carries [target]'s own visibility. Left at the default it would be public, which
     * for an `internal` or `private` class means a *public* entry point to a type its author
     * deliberately kept out of the module's API — and, on the JVM, an unexpected entry in the ABI
     * dump that `apiCheck` then fails on.
     */
    private fun SimpleFunctionBuildingContext.factoryShape(target: FirRegularClassSymbol) {
        visibility = target.rawStatus.visibility.orPublic()
        target.typeParameterSymbols.forEach { parameter ->
            typeParameter(parameter.name, key = DataApiPluginKey)
        }
        valueParameter(
            BLOCK_NAME,
            { typeParameters: List<FirTypeParameterRef> ->
                builderClassId(target)
                    .createConeType(session, typeParameters.toRefTypeArguments())
                    .toExtensionFunctionType()
            }
        )
    }

    override fun generateConstructors(
        context: MemberGenerationContext
    ): List<FirConstructorSymbol> =
        if (context.owner.classId in builderOwners) listOf(
            createConstructor(
                context.owner,
                DataApiPluginKey,
                isPrimary = true,
                generateDelegatedNoArgConstructorCall = true
            ).symbol
        ) else emptyList()

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(PREDICATE)
        register(LOOKUP_PREDICATE)
    }

    private fun builderClassId(dataOwner: FirRegularClassSymbol): ClassId =
        dataOwner.classId.createNestedClassId(BUILDER_NAME)

    /** The type parameters of [this] used as type arguments — `Box<T>` for `class Box<T>`. */
    private fun FirClassSymbol<*>.ownTypeArguments(): Array<ConeTypeProjection> =
        typeParameterSymbols.toTypeArguments()

    private fun List<FirTypeParameterSymbol>.toTypeArguments(): Array<ConeTypeProjection> =
        map { ConeTypeParameterTypeImpl(it.toLookupTag(), isMarkedNullable = false) }.toTypedArray()

    @JvmName("typeParametersToTypeArguments")
    private fun List<FirTypeParameter>.toTypeArguments(): Array<ConeTypeProjection> =
        map { it.symbol }.toTypeArguments()

    private fun List<FirTypeParameterRef>.toRefTypeArguments(): Array<ConeTypeProjection> =
        map { it.symbol }.toTypeArguments()

    private fun FirRegularClassSymbol.targetType(
        arguments: Array<ConeTypeProjection> = ownTypeArguments()
    ): ConeKotlinType = constructType(arguments, false, ConeAttributes.Empty)

    /**
     * Rewrites a type written in terms of the `@DataApi` class's type parameters into one written
     * in terms of the [builder]'s own copies of them, which is what every declaration generated
     * into the builder is typed with.
     */
    private fun builderSubstitutor(
        dataOwner: FirRegularClassSymbol,
        builder: FirClassSymbol<*>
    ): ConeSubstitutor =
        if (dataOwner.typeParameterSymbols.isEmpty()) ConeSubstitutor.Empty
        else substitutorByMap(
            dataOwner.typeParameterSymbols
                .zip(builder.typeParameterSymbols)
                .associate { (owned, mirrored) ->
                    owned to ConeTypeParameterTypeImpl(
                        mirrored.toLookupTag(),
                        isMarkedNullable = false
                    )
                },
            session
        )

    private fun ConeKotlinType.toExtensionFunctionType(): ConeKotlinType =
        FunctionTypeKind.Function.numberedClassId(1)
            .createConeType(
                session,
                arrayOf(this, session.builtinTypes.unitType.coneType)
            )
            .withAttributes(
                ConeAttributes.create(listOf(CompilerConeAttributes.ExtensionFunctionType))
            )

    /** The primary constructor of a `@DataApi` class, whose parameters the builder mirrors. */
    @OptIn(DirectDeclarationsAccess::class)
    private fun primaryConstructorOf(owner: FirRegularClassSymbol): FirConstructorSymbol? =
        owner.declarationSymbols
            .filterIsInstance<FirConstructorSymbol>()
            .firstOrNull { it.isPrimary }

    /**
     * The name of each builder property and whether it needs an `$isAssigned` flag alongside it —
     * everything [getCallableNamesForClass] needs, and nothing that would require a resolved type,
     * which is unavailable at the phase it can be called from.
     *
     * [getCallableNamesForClass] is called as early as supertype resolution — another plugin
     * generating declarations for the same class is enough to pull it there, since supertypes of
     * generated nested classes are resolved by walking their declared member scopes — so asking for
     * a resolved type here fails with "Expected is FirResolvedTypeRef, but was FirUserTypeRefImpl"
     * rather than resolving anything.
     */
    private fun dataPropertyNames(owner: FirRegularClassSymbol): List<Pair<Name, Boolean>> {
        val typeParameterNames = owner.typeParameterSymbols.mapTo(mutableSetOf()) { it.name }
        return primaryConstructorOf(owner)
            ?.valueParameterSymbols
            ?.map { it.name to it.tracksAssignment(typeParameterNames) }
            ?: emptyList()
    }

    /**
     * Whether the builder has to record that this property was assigned, rather than reading its
     * value being non-null as the same thing.
     *
     * Two properties need that. One with a **default value**, because the builder types every
     * property as nullable and so cannot otherwise tell "left unset" from "explicitly assigned
     * `null`" — only the former may fall back to the default. And one typed as a **bare type
     * parameter**, because `null` is a value it legitimately holds whenever the caller instantiates
     * that parameter with a nullable type (`Response<String?, …>`), so what makes it missing is not
     * having been assigned at all.
     */
    private fun FirValueParameterSymbol.tracksAssignment(typeParameterNames: Set<Name>): Boolean =
        hasDefaultValue || isBareTypeParameter(typeParameterNames)

    /**
     * Whether this parameter is declared as one of [typeParameterNames] used bare and non-nullable —
     * `val data: T`, and not `T?`, `List<T>` or anything else that mentions it.
     *
     * Read off the *unresolved* type reference where necessary, for the reason [dataPropertyNames]
     * gives. Type parameters shadow classifiers of the same name, so matching the name is exactly
     * what resolution would do, and the IR side — which sees resolved types — reaches the same
     * answer.
     */
    @OptIn(SymbolInternals::class)
    private fun FirValueParameterSymbol.isBareTypeParameter(typeParameterNames: Set<Name>): Boolean {
        if (typeParameterNames.isEmpty()) return false
        return when (val typeRef = fir.returnTypeRef) {
            is FirResolvedTypeRef -> typeRef.coneType.let {
                it is ConeTypeParameterType && !it.isMarkedNullable
            }
            is FirUserTypeRef -> !typeRef.isMarkedNullable &&
                typeRef.qualifier.singleOrNull()?.let {
                    it.name in typeParameterNames && it.typeArgumentList.typeArguments.isEmpty()
                } == true
            else -> false
        }
    }

    @OptIn(DirectDeclarationsAccess::class)
    private fun dataProperties(owner: FirRegularClassSymbol): List<DataProperty> {
        val constructor = primaryConstructorOf(owner) ?: return emptyList()
        val typeParameterNames = owner.typeParameterSymbols.mapTo(mutableSetOf()) { it.name }
        val visibilities = owner.declarationSymbols
            .filterIsInstance<FirPropertySymbol>()
            .associate { it.name to it.rawStatus.visibility }
        return constructor.valueParameterSymbols.map { parameter ->
            DataProperty(
                name = parameter.name,
                builderType = parameter.resolvedReturnType
                    .withNullability(true, session.typeContext),
                tracksAssignment = parameter.tracksAssignment(typeParameterNames),
                visibility = visibilities[parameter.name].orPublic()
            )
        }
    }

    /** An undeclared visibility resolves to `public`, the Kotlin default. */
    private fun Visibility?.orPublic(): Visibility = when (this) {
        Visibilities.Private, Visibilities.Protected, Visibilities.Internal -> this
        else -> Visibilities.Public
    }

    private fun dslMarkerAnnotation(): FirAnnotation = buildAnnotation {
        annotationTypeRef = buildResolvedTypeRef {
            coneType = DATA_API_DSL_ANNOTATION_CLASS_ID.createConeType(session)
        }
        argumentMapping = buildAnnotationArgumentMapping()
    }

    private class DataProperty(
        val name: Name,
        val builderType: ConeKotlinType,
        val tracksAssignment: Boolean,
        val visibility: Visibility
    )

}
