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
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.IrBasedDataClassMembersGenerator
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isNullable
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
 * Fills in the bodies of the `build`, `copy` and `invoke` functions that `DataApiBuilderGenerator`
 * declared at the FIR stage. The builder's mutable properties and the two constructors get their
 * bodies from Fir2Ir automatically; only these functions carry hand-rolled logic.
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
        if (origin is IrDeclarationOrigin.GeneratedByPlugin && origin.pluginKey == DataApiPluginKey) {
            when (declaration.name) {
                BUILD_NAME -> {
                    generateAssignmentTracking(declaration.parentAsClass)
                    generateBuildBody(declaration)
                }
                COPY_NAME -> generateCopyBody(declaration)
                INVOKE_NAME -> generateInvokeBody(declaration)
                EQUALS_NAME, HASHCODE_NAME, TO_STRING_NAME -> generateDataClassMember(declaration)
            }
        }
    }

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
     * Makes every defaulted property of [builderClass] record its own assignment, so that `build()`
     * can tell "left unset" from "explicitly assigned `null`" — a distinction the property value
     * alone cannot carry, since the builder types every property as nullable. The private
     * `<property>$isAssigned` flag declared alongside it in FIR is initialized to `false` and
     * flipped by the property's setter, whose Fir2Ir-generated body is replaced with:
     *
     * ```
     * set(value) {
     *     field = value
     *     greeting$isAssigned = true
     * }
     * ```
     *
     * `copy` drives the very same setters, so a copied instance carries its nulls over faithfully
     * instead of having them re-defaulted.
     */
    private fun generateAssignmentTracking(builderClass: IrClass) {
        val targetClass = builderClass.parentAsClass
        val constructor = targetClass.primaryConstructor ?: return
        constructor.parameters
            .filter { it.kind == IrParameterKind.Regular && it.defaultValue != null }
            .forEach { parameter ->
                val property = builderClass.properties.first { it.name == parameter.name }
                val flagField = builderClass.properties
                    .first { it.name == assignedFlagName(parameter.name) }
                    .backingField ?: return@forEach
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

            val requiredParameters = regularParameters.filterNot { it.type.isNullable() }
            if (requiredParameters.isNotEmpty()) {
                val missing = irTemporary(
                    irCall(mutableListOf, mutableListClass.typeWith(stringType), listOf(stringType))
                )
                requiredParameters.forEach { parameter ->
                    val unset = irEqualsNull(builderValue(parameter))
                    +irIfThen(
                        context.irBuiltIns.unitType,
                        if (parameter.defaultValue == null) unset
                        // a defaulted property can only go missing by an explicit `= null`
                        else irIfThenElse(
                            context.irBuiltIns.booleanType,
                            assignedFlag(parameter),
                            unset,
                            irFalse()
                        ),
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
            fun asDeclared(parameter: IrValueParameter, value: IrExpression): IrExpression =
                if (parameter.type.isNullable()) value
                else irCall(requireNotNull, parameter.type, listOf(parameter.type)).apply {
                    arguments[requireNotNull.owner.parameters.first()] = value
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
                val default = parameter.defaultValue
                val value = if (default == null) {
                    asDeclared(parameter, builderValue(parameter))
                } else {
                    irIfThenElse(
                        parameter.type,
                        assignedFlag(parameter),
                        asDeclared(parameter, builderValue(parameter)),
                        default.expression
                            .deepCopyWithSymbols(function)
                            .bindToResolved(resolved)
                    )
                }
                resolved[parameter] = irTemporary(
                    value,
                    nameHint = parameter.name.asString(),
                    irType = parameter.type
                )
            }

            val constructorCall = irCallConstructor(constructor.symbol, emptyList())
            regularParameters.forEach { parameter ->
                constructorCall.arguments[parameter] = irGet(resolved.getValue(parameter))
            }
            +irReturn(constructorCall)
        }
    }

    /**
     * The `Builder` nested class of a `@DataApi` class, together with the two members the `invoke`
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

    /** Emits `block(instance); return instance.build()` — the shared tail of `invoke` and `copy`. */
    private fun IrBlockBodyBuilder.applyBlockAndBuild(
        instance: IrVariable,
        block: IrValueParameter,
        buildFunction: IrSimpleFunction
    ) {
        val invoke = context.irBuiltIns.functionN(1).functions.first { it.name == INVOKE_NAME }
        +irCall(invoke).apply {
            arguments[invoke.dispatchReceiverParameter!!] = irGet(block)
            arguments[invoke.parameters.first { it.kind == IrParameterKind.Regular }] =
                irGet(instance)
        }
        +irReturn(
            irCall(buildFunction).apply {
                arguments[buildFunction.dispatchReceiverParameter!!] = irGet(instance)
            }
        )
    }

    /** `invoke(block)`: `val b = Builder(); block(b); return b.build()`. */
    private fun generateInvokeBody(function: IrSimpleFunction) {
        val companionClass = function.parentAsClass
        val targetClass = companionClass.parentAsClass
        val members = targetClass.builderMembers() ?: return
        val block = function.parameters.first { it.kind == IrParameterKind.Regular }
        val builder = DeclarationIrBuilder(context, function.symbol)
        function.body = builder.irBlockBody {
            val instance = irTemporary(irCallConstructor(members.constructor.symbol, emptyList()))
            applyBlockAndBuild(instance, block, members.buildFunction)
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
        val builder = DeclarationIrBuilder(context, function.symbol)
        function.body = builder.irBlockBody {
            val instance = irTemporary(irCallConstructor(members.constructor.symbol, emptyList()))
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
            applyBlockAndBuild(instance, block, members.buildFunction)
        }
    }

}
