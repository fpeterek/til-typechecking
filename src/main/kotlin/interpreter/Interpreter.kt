package org.fpeterek.tilscript.interpreter.interpreter

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.fpeterek.tilscript.interpreter.astprocessing.ASTConverter
import org.fpeterek.tilscript.interpreter.astprocessing.AntlrVisitor
import org.fpeterek.tilscript.interpreter.astprocessing.ErrorListener
import org.fpeterek.tilscript.interpreter.astprocessing.result.Sentences
import org.fpeterek.tilscript.interpreter.interpreter.builtins.*
import org.fpeterek.tilscript.interpreter.interpreter.interpreterinterface.FunctionInterface
import org.fpeterek.tilscript.interpreter.interpreter.interpreterinterface.InterpreterInterface
import org.fpeterek.tilscript.interpreter.reporting.Report
import org.fpeterek.tilscript.interpreter.reporting.ReportFormatter
import org.fpeterek.tilscript.interpreter.sentence.*
import org.fpeterek.tilscript.interpreter.types.*
import org.fpeterek.tilscript.interpreter.types.Util.isGeneric
import org.fpeterek.tilscript.interpreter.util.SrcPosition
import org.fpeterek.tilscript.parser.TILScriptLexer
import org.fpeterek.tilscript.parser.TILScriptParser


class Interpreter: InterpreterInterface {

    private val symbolRepo = SymbolRepository()
    private val typeRepo = TypeRepository()

    private val topLevelFrame = StackFrame(parent = null)

    private val stack: MutableList<StackFrame> = mutableListOf(topLevelFrame)

    private val currentFrame get() = stack.last()

    private val functions = mutableMapOf<String, TilFunction>()

    private val operatorFns = setOf("+", "-", "*", "/", "=", "<", ">")

    private val equalityOperator = EqualityOperator

    init {
        BuiltinsList.types.forEach(typeRepo::process)
        BuiltinsList.values.forEach(symbolRepo::define)

        BuiltinsList.functions.forEach { fn ->
            symbolRepo.define(fn.tilFunction)
            functions[fn.name] = fn.tilFunction
        }
    }

    private val numericOperators = mutableMapOf(
        "+" to NumericOperators.Plus,
        "-" to NumericOperators.Minus,
        "*" to NumericOperators.Multiply,
        "/" to NumericOperators.Divide,
        ">" to NumericOperators.Greater,
        "<" to NumericOperators.Less,
    )

    private fun defaultFrame() = StackFrame(parent = currentFrame)

    private fun pushFrame(frame: StackFrame) = stack.add(frame)
    private fun pushFrame() = pushFrame(defaultFrame())
    private fun popFrame() = stack.removeLast()

    private fun <T> withFrame(frame: StackFrame, fn: () -> T): T {
        pushFrame(frame)
        val result = fn()
        popFrame()
        return result
    }

    private fun <T> withFrame(fn: () -> T): T = withFrame(defaultFrame(), fn)

    private infix fun Type.matches(other: Type) = TypeMatcher.match(this, other, typeRepo)

    private fun findVar(frame: StackFrame?, name: String): Variable = when {
        frame == null -> throw RuntimeException("Variable not found '$name'")
        name in frame -> frame[name]!!
        else          -> findVar(frame.parent, name)
    }

    private fun findVar(name: String) = findVar(currentFrame, name)

    override fun fnArgsMatch(fn: FunctionType, types: List<Type>): List<Boolean> =
        TypeMatcher.matchFnArgs(fn, types, typeRepo)

    override fun fnSignatureMatch(fn: FunctionType, returned: Type, args: List<Type>): Pair<Boolean, List<Boolean>> =
        TypeMatcher.matchFn(fn, returned, args, typeRepo)

    private fun interpret(variable: Variable): Construction {

        val frameVar = findVar(variable.name)

        if (frameVar.value == null) {
            throw RuntimeException("Variable '${variable.name}' is declared but undefined")
        }

        if (!(frameVar.constructedType matches variable.constructedType)) {
            throw RuntimeException("Mismatch between expected type (${variable.constructedType}) and actual type of variable (${frameVar.constructedType})")
        }

        return frameVar.value
    }

    fun getFunction(fn: String): TilFunction =
        functions[fn] ?: throw RuntimeException("Function '$fn' is not declared")

    fun getSymbol(symbol: String): Symbol = Symbol(
        symbol,
        SrcPosition(-1, -1),
        symbolRepo[symbol] ?: throw RuntimeException("Unknown symbol '${symbol}'"),
        listOf()
    )

    fun getSymbol(symbol: Symbol): Symbol = Symbol(
        symbol.value,
        symbol.position,
        symbolRepo[symbol.value] ?: throw RuntimeException("Unknown symbol '${symbol.value}'"),
        symbol.reports
    )

    fun getVariable(name: String): Variable = currentFrame.getVar(name)

    private fun interpret(triv: Trivialization) = when {
        triv.construction is TilFunction && triv.construction.name in numericOperators ->
            numericOperators[triv.construction.name]!!.tilFunction

        triv.construction is TilFunction && triv.construction.name == "=" -> EqualityOperator.tilFunction

        triv.construction is TilFunction -> getFunction(triv.construction.name)

        triv.construction is Symbol && triv.construction.constructionType is Unknown ->
            getSymbol(triv.construction)

        triv.construction is Variable -> getVariable(triv.construction.name)

        else -> triv.construction
    }

    private fun execute(construction: Construction, executions: Int): Construction = if (executions > 0) {
        execute(interpret(construction), executions-1)
    } else {
        construction
    }

    private fun interpret(execution: Execution) = execute(execution.construction, execution.executionOrder)

    private fun createLambdaCapture() = LambdaContext(currentFrame)

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
            LambdaFunction(closure.variables, closure.construction, createLambdaCapture()),
        )
    }

    // TODO: Type-aware equality, probably built into the interpreter
    private fun interpretEquality(args: List<Construction>) = EqualityOperator.apply(this, args)

    private fun interpretNumericOperator(fn: TilFunction, args: List<Construction>): Construction =
        (numericOperators[fn.name] ?: throw RuntimeException("No such operator '${fn.name}'"))
            .apply(this, args)

    private fun interpretOperator(fn: TilFunction, comp: Composition): Construction {

        if (comp.args.size != 2) {
            throw RuntimeException("Operator '${fn.name}' expects 2 arguments (${comp.args.size} provided)")
        }

        return when (fn.name) {
            "="  -> interpretEquality(comp.args.map { interpret(it) })
            else -> interpretNumericOperator(fn, comp.args)
        }
    }

    private fun interpretFn(fn: FunctionInterface, args: List<Construction>) = withFrame {
        fn(this, args)
    }

    private fun interpretLambda(fn: LambdaFunction, args: List<Construction>) = withFrame(fn.context.frame) {
        fn(this, args)
    }

    private fun interpretFn(fn: TilFunction, comp: Composition): Construction {
        val fnImpl = when (fn.implementation) {
            null -> functions[fn.name]?.implementation
            else -> fn.implementation
        } ?: throw RuntimeException("Function ${fn.name} is declared but undefined, application is impossible")

        return when (fnImpl) {
            is LambdaFunction -> interpretLambda(fnImpl, comp.args)
            else              -> interpretFn(fnImpl, comp.args)
        }
    }

    private fun interpret(comp: Composition): Construction {
        val fn = interpret(comp.function)

        if (fn !is TilFunction) {
            throw RuntimeException("Only functions can be applied on arguments. $fn is not a function")
        }

        return when (fn.name) {
            in operatorFns -> interpretOperator(fn, comp)
            else           -> interpretFn(fn, comp)
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

        if (it.constructedType !is FunctionType) {
            throw RuntimeException("Invalid function type")
        }

        if (it.constructedType.imageType is GenericType) {
            val genTypes = it.constructedType.argTypes.filterIsInstance<GenericType>().map { it.argNumber }.toSet()

            if (it.constructedType.imageType.argNumber !in genTypes) {
                throw RuntimeException("Image type of function ${it.name} could not be deduced from type arguments")
            }
        }


        if (it.name !in functions) {
            functions[it.name] = it
        } else {
            val fn = functions[it.name]!!

            if (!(fn.constructedType matches it.constructedType)) {
                throw RuntimeException("Redeclaration of function '${fn.name}' with a different type")
            }
        }
    }

    private fun interpret(def: FunctionDefinition) {

        if (def.signature.imageType is GenericType) {
            val genTypes = def.args.filterIsInstance<GenericType>().map { it.argNumber }.toSet()

            if (def.signature.imageType.argNumber !in genTypes) {
                throw RuntimeException("Image type of function ${def.name} could not be deduced from type arguments")
            }
        }

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

            if (it.constructedType.isGeneric) {
                invalidUseOfGenerics()
            }

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

        if (alias.type.isGeneric) {
            invalidUseOfGenerics()
        }

        if (alias.name in typeRepo) {
            val declaredType = typeRepo[alias.name]!!
            if (!(declaredType matches alias.type)) {
                throw RuntimeException("Redeclaration of symbol '${alias.name}' with a different type")
            }
        } else {
            typeRepo.process(alias)
        }
    }

    private fun invalidUseOfGenerics(): Nothing =
        throw RuntimeException("Generic types are only allowed in function definitions")

    private fun interpret(varDecl: VariableDeclaration) {
        varDecl.variables.forEach {

            if (it.constructedType.isGeneric) {
                invalidUseOfGenerics()
            }

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

        if (varDef.constructsType.isGeneric) {
            invalidUseOfGenerics()
        }

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

    private fun interpret(sentence: Sentence) {
        when (sentence) {
            is Construction -> interpret(sentence)
            is Declaration  -> interpret(sentence)
        }
    }

    private fun interpret(sentences: Iterable<Sentence>) = sentences.forEach {
        try {
            interpret(it)
        } catch (e: Exception) {
            println("Runtime error: ${e.message}")
            return@forEach
        }
    }

    private fun printErrors(errors: Iterable<Report>, file: String, errorType: String) {
        println("-".repeat(80))
        println("File: $file")
        println("$errorType errors")

        ReportFormatter(file).terminalOutput(errors)

        println("-".repeat(80))
        println("\n")
    }

    fun interpretFile(file: String) {

        val stream = CharStreams.fromFileName(file)

        val errorListener = ErrorListener()

        val lexer = TILScriptLexer(stream)
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        val parser = TILScriptParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val start = parser.start()

        val sentences = try {
            AntlrVisitor.visit(start)
        } catch (ignored: Exception) {
            Sentences(listOf(), SrcPosition(0, 0))
        }

        if (errorListener.hasErrors) {
            printErrors(errorListener.errors, file, "Syntax")
            return
        }

        val ctx = ASTConverter.convert(sentences)

        interpret(ctx.sentences)
    }

}
