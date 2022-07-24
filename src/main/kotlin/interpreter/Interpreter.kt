package org.fpeterek.til.typechecking.interpreter

import org.fpeterek.til.typechecking.interpreter.interpreterinterface.InterpreterInterface
import org.fpeterek.til.typechecking.sentence.*
import org.fpeterek.til.typechecking.typechecker.TypeMatcher
import org.fpeterek.til.typechecking.types.SymbolRepository
import org.fpeterek.til.typechecking.types.Type
import org.fpeterek.til.typechecking.types.TypeRepository
import java.util.StringJoiner


class Interpreter: InterpreterInterface {

    private val symbolRepo = SymbolRepository()
    private val typeRepo = TypeRepository()

    private val topLevelFrame = StackFrame(parent = null)

    private val stack: MutableList<StackFrame> = mutableListOf(topLevelFrame)

    private val currentFrame get() = stack.last()

    private val functions = mutableMapOf<String, TilFunction>()

    private fun pushFrame() = stack.add(StackFrame(parent = currentFrame))
    private fun popFrame() = stack.removeLast()

    private fun <T> withFrame(fn: () -> T): T {
        pushFrame()
        val result = fn()
        popFrame()
        return result
    }

    private infix fun Type.matches(other: Type) = TypeMatcher.match(this, other, typeRepo)

    private fun findVar(frame: StackFrame?, name: String): Variable = when {
        frame == null -> throw RuntimeException("Variable not found '$name'")
        name in frame -> frame[name]!!
        else          -> findVar(frame.parent, name)
    }

    private fun findVar(name: String) = findVar(currentFrame, name)

    private fun interpret(variable: Variable): Construction {

        if (variable.value == null) {
            throw RuntimeException("Variable '${variable.name}' is declared but undefined")
        }

        val frameVar = findVar(variable.name)

        if (!(frameVar.constructedType matches variable.constructedType)) {
            throw RuntimeException("Mismatch between expected type (${variable.constructedType}) and actual type of variable (${frameVar.constructedType})")
        }

        return variable.value
    }

    private fun interpret(triv: Trivialization) = triv.construction

    private fun execute(construction: Construction, executions: Int): Construction = if (executions > 0) {
        execute(interpret(construction), executions-1)
    } else {
        construction
    }

    private fun interpret(execution: Execution) = execute(execution.construction, execution.executionOrder)

    private fun createLambdaCapture(closure: Closure) = LambdaContext(
        LambdaCaptureCreator(currentFrame).captureVars(closure.construction)
    )

    private fun interpret(closure: Closure): TilFunction = withFrame {
        // We want to put variables introduced by the closure on the stack even if we aren't calling the
        // resulting function as of now to avoid capturing variables with the same name from a higher scope
        // This is necessary because we use the call stack to create captures
        closure.variables.forEach(currentFrame::putVar)

        TilFunction(
            "<Lambda>",
            closure.position,
            closure.constructedType,
            closure.reports,
            LambdaFunction(closure.variables, closure.construction, createLambdaCapture(closure)),
        )
    }

    private fun interpret(comp: Composition): Construction {
        val fn = interpret(comp.function)

        if (fn !is TilFunction) {
            throw RuntimeException("Only functions can be applied on arguments. $fn is not a function")
        }

        val fnImpl = when (fn.implementation) {
            null -> functions[fn.name]?.implementation
            else -> fn.implementation
        } ?: throw RuntimeException("Function ${fn.name} is declared but undefined, application is impossible")

        return withFrame {
            fnImpl.apply(this, comp.args)
        }
    }

    // TODO: Test
    override fun interpret(construction: Construction): Construction = when (construction) {
        is Closure        -> interpret(construction)
        is Composition    -> interpret(construction)
        is Execution      -> interpret(construction)
        is Trivialization -> interpret(construction)
        // Values cannot be executed as they by themselves do not construct anything
        // Nil also only ever constructs nil, but Nil is a Value
        is Value          -> nil
        // Functions too cannot be executed, functions can only be applied using compositions
        // Functions must be constructed using trivializations or closures
        is TilFunction    -> nil
        is Variable       -> interpret(construction)
    }

    override fun typesMatch(t1: Type, t2: Type) = t1 matches t2

    override fun ensureMatch(expected: Type, received: Type) {
        if (!(expected matches received)) {
            throw RuntimeException("Type mismatch (expected: $expected, received: $received)")
        }
    }

    override fun createLocal(variable: Variable, value: Construction) {

        if (variable.name in currentFrame) {
            throw RuntimeException("Redefinition of variable '${variable.name}'")
        }

        val varWithValue = Variable(
            variable.name,
            variable.position,
            variable.constructedType,
            variable.reports,
            value,
        )

        currentFrame.putVar(varWithValue)
    }

    private fun interpret(decl: FunctionDeclaration) = decl.functions.forEach {
        if (it.name !in functions) {
            functions[it.name] = it
        } else {
            val fn = functions[it.name]!!

            if (fn.constructedType matches it.constructedType) {
                functions[it.name] = it
            } else {
                throw RuntimeException("Redeclaration of function '${fn.name}' with a different type")
            }
        }
    }

    private fun interpret(def: FunctionDefinition) {
        if (def.name in functions) {
            val declared = functions[def.name]!!
            if (declared.implementation != null) {
                throw RuntimeException("Redefinition of function '${def.name}' with a conflicting implementation")
            }
            if (!(declared.constructedType matches def.signature)) {
                throw RuntimeException("Redeclaration of function '${def.name}' with a different type")
            }
        }
        functions[def.name] = def.tilFunction
    }

    private fun interpret(lit: LiteralDeclaration) {
        lit.literals.forEach {
            if (it.value in symbolRepo) {
                val declaredType = symbolRepo[it.value]!!
                if (!(declaredType matches lit.type)) {
                    throw RuntimeException("Redeclaration of symbol '${it.value}' with a different type")
                }
            } else {
                symbolRepo.declare(it)
            }
        }
    }

    private fun interpret(typedef: TypeDefinition) {
        val alias = typedef.alias
        if (alias.name in typeRepo) {
            val declaredType = typeRepo[alias.name]!!
            if (!(declaredType matches alias.type)) {
                throw RuntimeException("Redeclaration of symbol '${alias.name}' with a different type")
            }
        } else {
            typeRepo.process(alias)
        }
    }

    private fun interpret(varDecl: VariableDeclaration) {
        varDecl.variables.forEach {
            if (it.name in topLevelFrame) {
                val declared = topLevelFrame[it.name]!!
                if (!(declared.constructedType matches it.constructedType)) {
                    throw RuntimeException("Redeclaration of variable '${it.name}' with a different type")
                }
            } else {
                topLevelFrame.putVar(it)
            }
        }
    }

    private fun interpret(varDef: VariableDefinition) {

        if (varDef.name in topLevelFrame) {
            val declared = topLevelFrame[varDef.name]!!

            if (declared.value != null) {
                throw RuntimeException("Redefinition of variable '${varDef.name}' with a new value")
            }

            if (!(declared.constructedType matches varDef.constructsType)) {
                throw RuntimeException("Redeclaration of variable '${varDef.name}' with a different type")
            }
        }

        val value = interpret(varDef.construction)

        if (!(value.constructedType matches varDef.constructsType)) {
            throw RuntimeException("Type of value assigned to variable '${varDef.name}' does not match expected type " +
                    "(expected: ${varDef.constructsType}, received: ${value.constructedType})")
        }

        topLevelFrame.putVar(varDef.variable.withValue(value))
    }

    private fun interpret(declaration: Declaration) {
        when (declaration) {
            is FunctionDeclaration -> interpret(declaration)
            is FunctionDefinition  -> interpret(declaration)
            is LiteralDeclaration  -> interpret(declaration)
            is TypeDefinition      -> interpret(declaration)
            is VariableDeclaration -> interpret(declaration)
            is VariableDefinition  -> interpret(declaration)
        }
    }

    fun interpret(sentence: Sentence) {
        when (sentence) {
            is Construction -> interpret(sentence)
            is Declaration  -> interpret(sentence)
        }
    }

    fun interpret(sentences: Iterable<Sentence>) = sentences.forEach {
        try {
            interpret(it)
        } catch (e: Exception) {
            println("Runtime error: ${e.message}")
            return@forEach
        }
    }

}
