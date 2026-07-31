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

package com.xemantic.kotlin.data.api.compiler.ir

import com.xemantic.kotlin.data.api.compiler.DataApiPluginKey
import com.xemantic.kotlin.data.api.compiler.assignedFlagName
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irConcat
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irEqualsNull
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irFalse
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irImplicitCast
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTrue
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.IrBasedDataClassMembersGenerator
import org.jetbrains.kotlin.ir.util.IrTypeParameterRemapper
import org.jetbrains.kotlin.ir.util.TypeRemapper
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.types.isMarkedNullable
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Fills in the bodies of the `build`, `copy`, `equals`/`hashCode`/`toString` and factory functions
 * that `DataApiBuilderGenerator` declared at the FIR stage. The builder's mutable properties and the
 * two constructors get their bodies from Fir2Ir automatically; only these functions carry
 * hand-rolled logic.
 */
class DataApiIrGenerationExtension : IrGenerationExtension {

    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        moduleFragment.accept(DataApiIrVisitor(pluginContext), null)
    }

}

private val BUILDER_NAME = Name.identifier("Builder")
private val BUILD_NAME = Name.identifier("build")
private val COPY_NAME = Name.identifier("copy")
private val INVOKE_NAME = Name.identifier("invoke")
private val EQUALS_NAME = Name.identifier("equals")
private val HASHCODE_NAME = Name.identifier("hashCode")
private val TO_STRING_NAME = Name.identifier("toString")
private val ADD_NAME = Name.identifier("add")
private val IS_EMPTY_NAME = Name.identifier("isEmpty")

@OptIn(UnsafeDuringIrConstructionAPI::class, ObsoleteDescriptorBasedAPI::class)
private class DataApiIrVisitor(
    private val context: IrPluginContext
) : IrVisitorVoid() {

    /** Finder for stdlib declarations the generated bodies reference. */
    private val builtins = context.finderForBuiltins()

    private val requireNotNull = builtins.findFunctions(
        CallableId(FqName("kotlin"), Name.identifier("requireNotNull"))
    ).first { it.owner.parameters.size == 1 }

    private val anyHashCode: IrSimpleFunctionSymbol =
        context.irBuiltIns.anyClass.owner.functions.single { it.name == HASHCODE_NAME }.symbol

    private val stringType: IrType get() = context.irBuiltIns.stringType

    private val mutableListClass = context.irBuiltIns.mutableListClass

    /** `mutableListOf<E>()` — the no-argument overload returning an empty `MutableList`. */
    private val mutableListOf: IrSimpleFunctionSymbol = builtins.findFunctions(
        CallableId(FqName("kotlin.collections"), Name.identifier("mutableListOf"))
    ).first { function ->
        function.owner.parameters.none { it.kind == IrParameterKind.Regular }
    }

    /** `MutableList<E>.add(element: E): Boolean`. */
    private val mutableListAdd: IrSimpleFunctionSymbol =
        mutableListClass.owner.functions.single { function ->
            function.name == ADD_NAME &&
                function.parameters.count { it.kind == IrParameterKind.Regular } == 1
        }.symbol

    /** `Collection<E>.isEmpty(): Boolean`. */
    private val collectionIsEmpty: IrSimpleFunctionSymbol =
        context.irBuiltIns.collectionClass.owner.functions.single { function ->
            function.name == IS_EMPTY_NAME &&
                function.parameters.none { it.kind == IrParameterKind.Regular }
        }.symbol

    /** `IllegalArgumentException(message: String?)`. */
    private val illegalArgumentExceptionConstructor = builtins.findConstructors(
        ClassId(FqName("kotlin"), Name.identifier("IllegalArgumentException"))
    ).single { constructor ->
        val regular = constructor.owner.parameters.filter { it.kind == IrParameterKind.Regular }
        regular.size == 1 && regular.single().type.classOrNull == context.irBuiltIns.stringClass
    }

    override fun visitElement(element: IrElement) {
        element.acceptChildren(this, null)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
        val origin = declaration.origin
        if (origin !is IrDeclarationOrigin.GeneratedByPlugin || origin.pluginKey != DataApiPluginKey) {
            return
        }
        // Which body a generated function needs is decided by where it was generated *into*, not by
        // its name alone: a class may legally be called `copy` or `build`, and its factory function
        // — named after it — would otherwise be taken for the member of that name and handed to a
        // body generator that expects an enclosing class it does not have.
        val owner = declaration.parent as? IrClass
        when {
            owner == null -> generateFactoryBody(declaration)
            owner.isGeneratedBuilder() -> if (declaration.name == BUILD_NAME) {
                generateAssignmentTracking(owner)
                generateBuildBody(declaration)
            }
            owner.hasGeneratedBuilder() -> when (declaration.name) {
                COPY_NAME -> generateCopyBody(declaration)
                EQUALS_NAME, HASHCODE_NAME, TO_STRING_NAME -> generateDataClassMember(declaration)
            }
            // the remaining place a function is generated into is the type hosting the factories of
            // the `@DataApi` classes nested in it — an object, or a companion object
            else -> generateFactoryBody(declaration)
        }
    }

    /** Whether this class is a `Builder` this plugin generated, rather than one a user declared. */
    private fun IrClass.isGeneratedBuilder(): Boolean {
        val origin = origin
        return name == BUILDER_NAME &&
            origin is IrDeclarationOrigin.GeneratedByPlugin &&
            origin.pluginKey == DataApiPluginKey
    }

    /** Whether this class is a `@DataApi` class, i.e. one the plugin gave a `Builder`. */
    private fun IrClass.hasGeneratedBuilder(): Boolean =
        declarations.any { it is IrClass && it.isGeneratedBuilder() }

    /**
     * Fills `equals`/`hashCode`/`toString` by delegating to the compiler's own
     * [IrBasedDataClassMembersGenerator] — the same code path that generates these members for
     * `data` classes — over the `@DataApi` class's primary-constructor properties.
     */
    private fun generateDataClassMember(function: IrSimpleFunction) {
        val dataClass = function.parentAsClass
        val constructor = dataClass.primaryConstructor ?: return
        val properties = constructor.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .map { parameter -> dataClass.properties.first { it.name == parameter.name } }
        val generator = dataClassMembersGenerator(dataClass)
        when (function.name) {
            EQUALS_NAME -> generator.generateEqualsMethod(function, properties)
            HASHCODE_NAME -> generator.generateHashCodeMethod(function, properties)
            TO_STRING_NAME -> generator.generateToStringMethod(function, properties)
        }
    }

    private fun dataClassMembersGenerator(dataClass: IrClass): IrBasedDataClassMembersGenerator =
        object : IrBasedDataClassMembersGenerator(
            context = context,
            symbolTable = context.symbolTable,
            irClass = dataClass,
            fqName = dataClass.kotlinFqName,
            origin = IrDeclarationOrigin.GeneratedByPlugin(DataApiPluginKey),
            forbidDirectFieldAccess = false,
            generateBodies = true
        ) {
            override fun getProperty(irValueParameter: IrValueParameter?): IrProperty =
                dataClass.properties.single { it.name == irValueParameter!!.name }

            override fun generateSyntheticFunctionParameterDeclarations(irFunction: IrFunction) {}

            override fun getHashCodeFunctionInfo(type: IrType): HashCodeFunctionInfo =
                object : HashCodeFunctionInfo {
                    override val symbol: IrSimpleFunctionSymbol =
                        // an array hashes by content, exactly as in a `data` class — its own
                        // `hashCode` is `Any`'s, which would hash by identity
                        if (type.classifierOrNull.isArrayOrPrimitiveArray) {
                            context.irBuiltIns.dataClassArrayMemberHashCodeSymbol
                        } else {
                            type.hashCodeSymbol()
                        }

                    override fun commitSubstituted(
                        irMemberAccessExpression: IrMemberAccessExpression<*>
                    ) {
                    }
                }
        }

    /** The member `hashCode` of [this] type, falling back to `Any.hashCode` (virtual dispatch). */
    private fun IrType.hashCodeSymbol(): IrSimpleFunctionSymbol =
        classOrNull?.owner?.functions?.firstOrNull {
            it.name == HASHCODE_NAME &&
                it.dispatchReceiverParameter != null &&
                it.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
        }?.symbol ?: anyHashCode

    /**
     * Makes every property of [builderClass] that carries an `$isAssigned` flag record its own
     * assignment, so that `build()` can tell "left unset" from "explicitly assigned `null`" — a
     * distinction the property value alone cannot carry, since the builder types every property as
     * nullable. The private `<property>$isAssigned` flag declared alongside it in FIR is initialized
     * to `false` and flipped by the property's setter, whose Fir2Ir-generated body is replaced with:
     *
     * ```
     * set(value) {
     *     field = value
     *     greeting$isAssigned = true
     * }
     * ```
     *
     * Which properties get a flag is decided in FIR (see `DataApiBuilderGenerator.tracksAssignment`)
     * and read back here from the flags that are actually there, rather than derived a second time —
     * the two answers have to be the same one.
     *
     * `copy` drives the very same setters, so a copied instance carries its nulls over faithfully
     * instead of having them re-defaulted.
     */
    private fun generateAssignmentTracking(builderClass: IrClass) {
        val targetClass = builderClass.parentAsClass
        val constructor = targetClass.primaryConstructor ?: return
        constructor.parameters
            .filter { it.kind == IrParameterKind.Regular }
            .forEach { parameter ->
                val property = builderClass.properties.first { it.name == parameter.name }
                val flagField = builderClass.assignmentFlagOf(parameter)
                    ?.backingField ?: return@forEach
                val field = property.backingField ?: return@forEach
                val setter = property.setter ?: return@forEach
                val value = setter.parameters.first { it.kind == IrParameterKind.Regular }
                val setterReceiver = setter.dispatchReceiverParameter ?: return@forEach
                DeclarationIrBuilder(context, flagField.symbol).run {
                    flagField.initializer = irExprBody(irFalse())
                }
                DeclarationIrBuilder(context, setter.symbol).run {
                    setter.body = irBlockBody {
                        +irSetField(irGet(setterReceiver), field, irGet(value))
                        +irSetField(irGet(setterReceiver), flagField, irTrue())
                    }
                }
            }
    }

    /** The `<property>$isAssigned` flag of [parameter]'s builder property, if it has one. */
    private fun IrClass.assignmentFlagOf(parameter: IrValueParameter): IrProperty? =
        properties.firstOrNull { it.name == assignedFlagName(parameter.name) }

    /**
     * Whether [parameter] is typed as a bare type parameter — `val data: T`, not `T?` or `List<T>`.
     *
     * Such a property is *declared* non-null yet legitimately holds `null` whenever the caller
     * instantiates that type parameter with a nullable type, so neither its declared nullability nor
     * its value can say whether the caller supplied it. Its `$isAssigned` flag can.
     */
    private fun isBareTypeParameter(parameter: IrValueParameter): Boolean =
        parameter.type.classifierOrNull is IrTypeParameterSymbol &&
            !parameter.type.isMarkedNullable()

    /**
     * `build()`: validates that every required property was set, collecting **all** missing ones
     * into a single `IllegalArgumentException`, then resolves each property — falling back to the
     * primary constructor's default value where the builder left one unset — and constructs the
     * instance, e.g. for `class Person(val name: String, val age: Int?, val greeting: String = "hi $name")`:
     *
     * ```
     * fun build(): Person {
     *     val missing = mutableListOf<String>()
     *     if (name == null) missing.add("name")
     *     if (!missing.isEmpty())
     *         throw IllegalArgumentException("Cannot build Person: missing required properties $missing")
     *     val name0 = requireNotNull(name)
     *     val age0 = age
     *     val greeting0 = if (greeting == null) "hi $name0" else greeting
     *     return Person(name = name0, age = age0, greeting = greeting0)
     * }
     * ```
     *
     * The default expression is inlined here rather than left to the constructor call — omitting an
     * argument is a structural property of an IR call, while the builder only learns at runtime
     * which properties were assigned. Properties are resolved in declaration order and the inlined
     * expressions are rewired to the resolved locals, so a default referring to a preceding property
     * (`greeting` above) observes the same value the constructor would have seen.
     *
     * Whether to fall back to the default is decided by the `<property>$isAssigned` flag maintained
     * by [generateAssignmentTracking], never by the property being `null` — so an explicit
     * `greeting = null` is honoured as a null rather than silently selecting the default. A
     * defaulted property is therefore *required* only in the narrow sense of being explicitly
     * assigned `null` while its constructor parameter is non-nullable.
     */
    private fun generateBuildBody(function: IrSimpleFunction) {
        val builderClass = function.parentAsClass
        val targetClass = builderClass.parentAsClass
        val constructor = targetClass.primaryConstructor ?: return
        val receiver = function.dispatchReceiverParameter ?: return
        val regularParameters = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
        // everything read off the constructor is typed with the class's type parameters, while this
        // body lives in the builder, which mirrors them onto its own
        val toBuilder = targetClass.typeParameterRemapperTo(builderClass)
        val builderTypeArguments = builderClass.typeParameters.map { it.defaultType }
        val builder = DeclarationIrBuilder(context, function.symbol)
        function.body = builder.irBlockBody {

            fun builderValue(parameter: IrValueParameter): IrExpression {
                val getter = builderClass.properties.first { it.name == parameter.name }.getter!!
                return irCall(getter).apply {
                    arguments[getter.dispatchReceiverParameter!!] = irGet(receiver)
                }
            }

            fun assignedFlag(parameter: IrValueParameter): IrExpression {
                val getter = builderClass.properties
                    .first { it.name == assignedFlagName(parameter.name) }.getter!!
                return irCall(getter).apply {
                    arguments[getter.dispatchReceiverParameter!!] = irGet(receiver)
                }
            }

            /**
             * What makes [parameter] missing, or `null` when nothing can — it is either optional or
             * always resolvable.
             */
            fun missingCondition(parameter: IrValueParameter): IrExpression? {
                // *declared* nullability, not `isNullable()`: an unbounded type parameter is
                // nullable by its `Any?` bound, yet a property typed `T` rather than `T?` is one the
                // caller is still expected to supply
                if (parameter.type.isMarkedNullable()) return null
                val flagged = builderClass.assignmentFlagOf(parameter) != null
                return when {
                    // a bare type parameter holds `null` legitimately — for `Response<String?, …>`
                    // it is a `String?` — so what makes it missing is not having been assigned at
                    // all, which only its flag can tell. With a default value it can never be
                    // missing: unassigned simply falls back to that default
                    isBareTypeParameter(parameter) && flagged ->
                        if (parameter.defaultValue != null) null
                        else irEquals(assignedFlag(parameter), irFalse())
                    // a defaulted property can only go missing by an explicit `= null`
                    parameter.defaultValue != null && flagged -> irIfThenElse(
                        context.irBuiltIns.booleanType,
                        assignedFlag(parameter),
                        irEqualsNull(builderValue(parameter)),
                        irFalse()
                    )
                    else -> irEqualsNull(builderValue(parameter))
                }
            }

            val requiredParameters = regularParameters.filter { missingCondition(it) != null }
            if (requiredParameters.isNotEmpty()) {
                val missing = irTemporary(
                    irCall(mutableListOf, mutableListClass.typeWith(stringType), listOf(stringType))
                )
                requiredParameters.forEach { parameter ->
                    +irIfThen(
                        context.irBuiltIns.unitType,
                        missingCondition(parameter)!!,
                        irCall(mutableListAdd).apply {
                            arguments[mutableListAdd.owner.dispatchReceiverParameter!!] = irGet(missing)
                            arguments[mutableListAdd.owner.parameters.first { it.kind == IrParameterKind.Regular }] =
                                irString(parameter.name.asString())
                        }
                    )
                }
                +irIfThen(
                    context.irBuiltIns.unitType,
                    // missing.isEmpty() == false  ->  missing.isNotEmpty()
                    irEquals(
                        irCall(collectionIsEmpty).apply {
                            arguments[collectionIsEmpty.owner.dispatchReceiverParameter!!] = irGet(missing)
                        },
                        irFalse()
                    ),
                    IrThrowImpl(
                        startOffset,
                        endOffset,
                        context.irBuiltIns.nothingType,
                        irCallConstructor(illegalArgumentExceptionConstructor, emptyList()).apply {
                            arguments[illegalArgumentExceptionConstructor.owner.parameters.first { it.kind == IrParameterKind.Regular }] =
                                irConcat().apply {
                                    arguments.add(
                                        irString("Cannot build ${targetClass.name.asString()}: missing required properties ")
                                    )
                                    arguments.add(irGet(missing))
                                }
                        }
                    )
                )
            }

            /** `value` for a nullable parameter, `requireNotNull(value)` for a required one. */
            fun asDeclared(parameter: IrValueParameter, value: IrExpression): IrExpression {
                val type = toBuilder.remapType(parameter.type)
                if (type.isMarkedNullable()) return value
                // a bare type parameter must not be asserted non-null: `null` is what a
                // `Response<String?, …>` legitimately holds, and `requireNotNull` would reject it.
                // The flag has already established that the caller assigned this value, so it is
                // taken as the `T` it was assigned as
                if (isBareTypeParameter(parameter) &&
                    builderClass.assignmentFlagOf(parameter) != null
                ) {
                    return irImplicitCast(value, type)
                }
                // `requireNotNull` is bounded by `T : Any`, which a bare type parameter does not
                // satisfy — its definitely-non-null form does
                val notNull = type.makeNotNull()
                return irCall(requireNotNull, notNull, listOf(notNull)).apply {
                    arguments[requireNotNull.owner.parameters.first()] = value
                }
            }

            /**
             * Rewires references to the constructor's own parameters, which a copied default
             * expression carries over, to the locals already resolved by this `build()`.
             */
            fun IrExpression.bindToResolved(
                resolved: Map<IrValueParameter, IrVariable>
            ): IrExpression = transform(
                object : IrElementTransformerVoid() {
                    override fun visitGetValue(expression: IrGetValue): IrExpression {
                        val local = resolved[expression.symbol.owner as? IrValueParameter]
                        return if (local != null) irGet(local) else expression
                    }
                },
                null
            )

            val resolved = mutableMapOf<IrValueParameter, IrVariable>()
            regularParameters.forEach { parameter ->
                val type = toBuilder.remapType(parameter.type)
                val default = parameter.defaultValue
                val value = if (default == null) {
                    asDeclared(parameter, builderValue(parameter))
                } else {
                    irIfThenElse(
                        type,
                        assignedFlag(parameter),
                        asDeclared(parameter, builderValue(parameter)),
                        default.expression
                            .deepCopyWithSymbols(function) { toBuilder }
                            .bindToResolved(resolved)
                    )
                }
                resolved[parameter] = irTemporary(
                    value,
                    nameHint = parameter.name.asString(),
                    irType = type
                )
            }

            val constructorCall = irCallConstructor(constructor.symbol, builderTypeArguments)
                .apply { type = function.returnType }
            regularParameters.forEach { parameter ->
                constructorCall.arguments[parameter] = irGet(resolved.getValue(parameter))
            }
            +irReturn(constructorCall)
        }
    }

    /**
     * The `Builder` nested class of a `@DataApi` class, together with the two members the factory
     * and `copy` bodies drive it through.
     */
    private class BuilderMembers(
        val builderClass: IrClass,
        val constructor: IrConstructor,
        val buildFunction: IrSimpleFunction
    )

    /** Resolves the nested `Builder` of [this] `@DataApi` class, or `null` if it is absent. */
    private fun IrClass.builderMembers(): BuilderMembers? {
        val builderClass = declarations
            .filterIsInstance<IrClass>()
            .firstOrNull { it.name == BUILDER_NAME } ?: return null
        val constructor = builderClass.primaryConstructor ?: return null
        val buildFunction = builderClass.functions.first { it.name == BUILD_NAME }
        return BuilderMembers(builderClass, constructor, buildFunction)
    }

    /**
     * Emits `block(instance); return instance.build()` — the shared tail of the factory and `copy`.
     *
     * `build()` is typed by [resultType] rather than by its own return type, which is written in
     * terms of the builder's type parameters: at this call site the receiver is a `Builder<T>` for
     * the *caller's* `T`, so that is what the call produces.
     */
    private fun IrBlockBodyBuilder.applyBlockAndBuild(
        instance: IrVariable,
        block: IrValueParameter,
        buildFunction: IrSimpleFunction,
        resultType: IrType
    ) {
        val invoke = context.irBuiltIns.functionN(1).functions.first { it.name == INVOKE_NAME }
        +irCall(invoke.symbol, context.irBuiltIns.unitType).apply {
            arguments[invoke.dispatchReceiverParameter!!] = irGet(block)
            arguments[invoke.parameters.first { it.kind == IrParameterKind.Regular }] =
                irGet(instance)
        }
        +irReturn(
            irCall(buildFunction.symbol, resultType).apply {
                arguments[buildFunction.dispatchReceiverParameter!!] = irGet(instance)
            }
        )
    }

    /**
     * The factory function `Person(block)`: `val b = Builder(); block(b); return b.build()`.
     *
     * The target class is read off the return type rather than the function's parent, since the
     * factory lives outside it either way — at the top level for a top-level class, or in the
     * enclosing type's companion for a nested one.
     *
     * Neither host is generic even when the class is, so for a generic class the factory declares
     * type parameters of its own, and it is those — not the class's — that the `Builder` is
     * instantiated with.
     */
    private fun generateFactoryBody(function: IrSimpleFunction) {
        val targetClass = function.returnType.classOrNull?.owner ?: return
        val members = targetClass.builderMembers() ?: return
        val block = function.parameters.first { it.kind == IrParameterKind.Regular }
        val typeArguments = function.typeParameters.map { it.defaultType }
        val builderType = members.builderClass.symbol.typeWith(typeArguments)
        val builder = DeclarationIrBuilder(context, function.symbol)
        function.body = builder.irBlockBody {
            val instance = irTemporary(
                irCallConstructor(members.constructor.symbol, typeArguments)
                    .apply { type = builderType },
                irType = builderType
            )
            applyBlockAndBuild(instance, block, members.buildFunction, function.returnType)
        }
    }

    /**
     * `copy(block)`: pre-populates a fresh `Builder` from the receiver's current property values,
     * applies the caller's `block` (which may override any of them), then delegates to `build()` —
     * the `@DataApi` analogue of a `data` class `copy`, but driven by the builder DSL, e.g.
     *
     * ```
     * fun copy(block: Builder.() -> Unit): Person {
     *     val b = Builder()
     *     b.name = this.name
     *     b.age = this.age
     *     block(b)
     *     return b.build()
     * }
     * ```
     *
     * Because the result flows through `build()`, the same required-property validation applies: a
     * block that clears a required property (`name = null`) fails exactly as a fresh build would.
     */
    private fun generateCopyBody(function: IrSimpleFunction) {
        val targetClass = function.parentAsClass
        val members = targetClass.builderMembers() ?: return
        val constructor = targetClass.primaryConstructor ?: return
        val regularParameters = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
        val receiver = function.dispatchReceiverParameter ?: return
        val block = function.parameters.first { it.kind == IrParameterKind.Regular }
        // `copy` is a member of the class, so the builder it drives is instantiated with the
        // class's own type parameters
        val typeArguments = targetClass.typeParameters.map { it.defaultType }
        val builderType = members.builderClass.symbol.typeWith(typeArguments)
        val builder = DeclarationIrBuilder(context, function.symbol)
        function.body = builder.irBlockBody {
            val instance = irTemporary(
                irCallConstructor(members.constructor.symbol, typeArguments)
                    .apply { type = builderType },
                irType = builderType
            )
            regularParameters.forEach { parameter ->
                val getter = targetClass.properties.first { it.name == parameter.name }.getter!!
                val setter = members.builderClass.properties.first { it.name == parameter.name }.setter!!
                +irCall(setter).apply {
                    arguments[setter.dispatchReceiverParameter!!] = irGet(instance)
                    arguments[setter.parameters.first { it.kind == IrParameterKind.Regular }] =
                        irCall(getter).apply {
                            arguments[getter.dispatchReceiverParameter!!] = irGet(receiver)
                        }
                }
            }
            applyBlockAndBuild(instance, block, members.buildFunction, function.returnType)
        }
    }

    /**
     * Maps the type parameters of [this] class onto the ones [mirror] copied from it, so that a
     * type or an expression lifted out of the class can be re-typed for a body generated into the
     * mirroring class. Empty — and so an identity remapper — for a non-generic class.
     */
    private fun IrClass.typeParameterRemapperTo(mirror: IrClass): TypeRemapper =
        IrTypeParameterRemapper(typeParameters.zip(mirror.typeParameters).toMap())

}
