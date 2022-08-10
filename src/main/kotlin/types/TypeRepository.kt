package org.fpeterek.til.typechecking.types

import org.fpeterek.til.typechecking.interpreter.builtins.Types

class TypeRepository(withBuiltins: Boolean = false) {

    companion object {
        fun withBuiltins() = TypeRepository(withBuiltins=true)
    }

    private val types = mutableMapOf<String, Type>()

    init {
        if (withBuiltins) {
            Types.all.forEach(::addType)
        }
    }

    private fun addType(type: AtomicType) {
        types[type.name] = type
    }

    private fun addAlias(alias: TypeAlias) {
        types[alias.name] = alias
    }

    private fun storeAtomic(type: AtomicType) = type.apply(::addType)

    private fun storeAlias(alias: TypeAlias) = alias.apply(::addAlias)

    fun process(type: AtomicType) = when (type.name) {
        !in types -> type.apply(::addType)
        else -> type
    }

    fun process(alias: TypeAlias) = when (alias.name) {
        !in types -> alias.apply(::addAlias)
        else -> alias
    }

    fun process(functionType: FunctionType): FunctionType = FunctionType(
        process(functionType.imageType),
        functionType.argTypes.map(::process)
    )

    fun process(type: Type) = when (type) {
        is TypeAlias -> process(type)
        is AtomicType -> process(type)
        else -> type
    }

    operator fun get(name: String) = types[name]
    operator fun contains(name: String) = name in types

    fun isFunction(name: String): Boolean = when (types[name]) {
        null -> false
        is FunctionType -> true
        is TypeAlias -> isFunction((types[name]!! as TypeAlias).name)
        else -> false
    }

    fun isAtomic(name: String): Boolean = when (types[name]) {
        null -> false
        is AtomicType -> true
        is TypeAlias -> isAtomic((types[name]!! as TypeAlias).name)
        else -> false
    }

}
