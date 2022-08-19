package org.fpeterek.tilscript.interpreter.interpreter.builtins

import org.fpeterek.tilscript.interpreter.interpreter.interpreterinterface.EagerFunction
import org.fpeterek.tilscript.interpreter.interpreter.interpreterinterface.FnCallContext
import org.fpeterek.tilscript.interpreter.interpreter.interpreterinterface.InterpreterInterface
import org.fpeterek.tilscript.interpreter.sentence.*
import org.fpeterek.tilscript.interpreter.util.SrcPosition

object LogicFunctions {

    private val unary = listOf(
        Variable("fst", SrcPosition(-1, -1), Types.Bool),
    )

    private val binary = listOf(
        Variable("fst", SrcPosition(-1, -1), Types.Bool),
        Variable("snd", SrcPosition(-1, -1), Types.Bool),
    )

    private fun symbolicNil(ctx: FnCallContext) =
        Nil(ctx.position, reason="Cannot perform logic operations on symbolic values")

    object Not : EagerFunction(
        "Not",
        Types.Bool,
        unary
    ) {
        override fun apply(interpreter: InterpreterInterface, args: List<Construction>, ctx: FnCallContext) =
            when {
                args[0] is Bool -> Bool(!(args[0] as Bool).value, srcPos = ctx.position)
                else            -> symbolicNil(ctx)
            }
    }

    object And : EagerFunction(
        "And",
        Types.Bool,
        binary
    ) {
        override fun apply(interpreter: InterpreterInterface, args: List<Construction>, ctx: FnCallContext) =
            when {
                args.all { it is Bool && it.value } -> Bool(true, srcPos = ctx.position)
                args.all { it is Symbol } -> symbolicNil(ctx)
                else -> Bool(false, srcPos = ctx.position)
            }
    }

    object Or : EagerFunction(
        "Or",
        Types.Bool,
        binary
    ) {
        override fun apply(interpreter: InterpreterInterface, args: List<Construction>, ctx: FnCallContext) =
            when {
                args.any { it is Bool && it.value } -> Bool(true, srcPos = ctx.position)
                args.all { it is Symbol } -> symbolicNil(ctx)
                else -> Bool(false, srcPos = ctx.position)
            }
    }

    object Implies : EagerFunction(
        "Implies",
        Types.Bool,
        binary
    ) {
        override fun apply(interpreter: InterpreterInterface, args: List<Construction>, ctx: FnCallContext) =
            when {
                args[0] is Bool   && !(args[0] as Bool).value -> Bool(true, srcPos = ctx.position)
                args[1] is Bool   &&  (args[1] as Bool).value -> Bool(true, srcPos = ctx.position)
                args[0] is Symbol ||   args[1] is Symbol      -> symbolicNil(ctx)
                else                                          -> Bool(false, srcPos = ctx.position)
            }
    }

}