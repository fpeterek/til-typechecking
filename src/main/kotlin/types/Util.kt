package org.fpeterek.til.interpreter.types

import org.fpeterek.til.interpreter.sentence.*
import org.fpeterek.til.interpreter.interpreter.builtins.Types
import org.fpeterek.til.interpreter.types.Util.isGeneric
import org.fpeterek.til.interpreter.util.SrcPosition

object Util {

    val w = Variable("w", SrcPosition(-1, -1), Types.World)
    val t = Variable("t", SrcPosition(-1, -1), Types.Time)

    fun FunctionType.intensionalize() =
        FunctionType(FunctionType(this, Types.Time), Types.World)

    fun AtomicType.intensionalize() =
        FunctionType(FunctionType(this, Types.Time), Types.World)

    fun Closure.intensionalize() =
        Closure(listOf(w), Closure(listOf(t), this, srcPos=position), srcPos=position)

    fun <T : Construction> T.trivialize() =
        Trivialization(
            construction=this,
            constructedType=constructionType,
            constructionType=ConstructionType,
            srcPos=position
        )

    fun Composition.extensionalize(w: Construction, t: Construction) =
        this.compose(w).compose(t)

    fun TilFunction.extensionalize(w: Construction, t: Construction) =
        this.trivialize().compose(w).compose(t)

    fun Construction.compose(vararg args: Construction) = when (this) {
        is TilFunction -> Composition(this.trivialize(), args.toList(), position)
        else -> Composition(this, args.toList(), position)
    }

    val AtomicType.isGeneric       get() = false
    val ConstructionType.isGeneric get() = false
    val FunctionType.isGeneric     get() = imageType.isGeneric || argTypes.any { it.isGeneric }
    val GenericType.isGeneric      get() = true
    val ListType.isGeneric         get() = type.isGeneric
    val TupleType.isGeneric        get() = types.any { it.isGeneric }
    val TypeAlias.isGeneric        get() = type.isGeneric
    val Unknown.isGeneric          get() = false

    val Type.isGeneric: Boolean
        get() = when (this) {
            is AtomicType       -> isGeneric
            is ConstructionType -> isGeneric
            is FunctionType     -> isGeneric
            is GenericType      -> isGeneric
            is ListType         -> isGeneric
            is TupleType         -> isGeneric
            is TypeAlias        -> isGeneric
            is Unknown          -> isGeneric
        }
}
