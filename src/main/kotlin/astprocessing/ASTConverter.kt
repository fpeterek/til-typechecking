package org.fpeterek.til.typechecking.astprocessing

import org.fpeterek.til.typechecking.astprocessing.result.*
import org.fpeterek.til.typechecking.astprocessing.result.Construction.*
import org.fpeterek.til.typechecking.sentence.*
import org.fpeterek.til.typechecking.tilscript.Builtins
import org.fpeterek.til.typechecking.tilscript.ScriptContext
import org.fpeterek.til.typechecking.types.*
import org.fpeterek.til.typechecking.types.TypeAlias as TilTypeAlias
import org.fpeterek.til.typechecking.sentence.Construction as TilConstruction
import org.fpeterek.til.typechecking.sentence.Execution as TilExecution
import org.fpeterek.til.typechecking.sentence.Composition as TilComposition
import org.fpeterek.til.typechecking.sentence.Closure as TilClosure
import org.fpeterek.til.typechecking.sentence.Variable as TilVariable
import org.fpeterek.til.typechecking.sentence.Trivialization as TilTrivialization


class ASTConverter private constructor() {

    companion object {
        fun convert(sentences: Sentences) = ASTConverter().convert(sentences)
    }

    private val lits = mutableSetOf<String>()
    private val fns = mutableSetOf<String>()

    private val repo = TypeRepository.withBuiltins()

    init {
        fns.addAll(Builtins.builtinFunctions.asSequence().map { it.name })
        lits.addAll(Builtins.builtinValues.asSequence().map { it.value })
    }

    private fun convert(sentences: Sentences) = ScriptContext(
        sentences=sentences.sentences.map(::convertSentence),
        types=repo,
    )

    private fun convertSentence(sentence: IntermediateResult) = when (sentence) {
        is Construction -> convertConstruction(sentence)
        is GlobalVarDef -> convertGlobalVarDef(sentence)
        is EntityDef    -> convertEntityDef(sentence)
        is TypeAlias    -> convertTypeAlias(sentence)

        else -> throw RuntimeException("Invalid parser state")
    }

    private fun convertConstruction(construction: Construction): TilConstruction = when (construction) {
        is Closure     -> convertClosure(construction)
        is Composition -> convertComposition(construction)
        is Execution   -> convertExecution(construction)
        is VarRef      -> convertVarRef(construction)
    }

    private fun convertGlobalVarDef(def: GlobalVarDef) = VariableDefinition(
        def.vars.map { it.name },
        convertDataType(def.type)
    )

    private fun convertEntityDef(entityDef: EntityDef) = convertDataType(entityDef.type).let { type ->
        when {
            type is FunctionType || repo.isFunction(type.name) -> FunctionDefinition(entityDef.names, type)
            else -> LiteralDefinition(entityDef.names, type)
        }
    }.apply {
        when (this) {
            is FunctionDefinition -> fns.addAll(names)
            is LiteralDefinition -> lits.addAll(names)
            else -> throw RuntimeException("Invalid state")
        }
    }

    private fun processTypeAlias(typeAlias: TypeAlias) = TilTypeAlias(
        shortName="",
        name=typeAlias.name,
        type=convertDataType(typeAlias.type)
    ).let { repo.process(it) }

    private fun convertTypeAlias(typeAlias: TypeAlias) = TypeDefinition(processTypeAlias(typeAlias))

    private fun convertClosure(closure: Closure): TilClosure = TilClosure(
        variables=closure.vars.map(::convertTypedVar),
        construction=convertConstruction(closure.construction)
    )

    private fun convertTypedVar(typedVar: TypedVar) = TilVariable(
        typedVar.name,
        repo[typedVar.type] ?: throw RuntimeException("Unknown type: ${typedVar.name}")
    )

    private fun convertComposition(composition: Composition) = TilComposition(
        function=convertConstruction(composition.fn),
        args=composition.args.map(::convertConstruction)
    )

    private fun convertExecution(execution: Execution): TilConstruction = when (execution.order) {
        0    -> convertTilTrivialization(execution)
        1, 2 -> convertTilExecution(execution)
        else -> throw RuntimeException("Invalid parser state")
    }

    private fun convertTilExecution(execution: Execution) = TilExecution(
        construction=when (execution.construction) {
            is Construction -> convertConstruction(execution.construction)
            else -> throw RuntimeException("Invalid parser state")
        },
        executionOrder=execution.order
    )

    private fun convertTilTrivialization(execution: Execution) = TilTrivialization(
        construction=when (execution.construction) {
            is Construction -> convertConstruction(execution.construction)
            is Entity -> convertEntityRef(execution.construction)
            else -> throw RuntimeException("Invalid parser state")
        }
    )

    private fun convertEntityRef(entity: Entity): TilConstruction = when (entity) {
        is Entity.Number -> convertNumLiteral(entity)
        is Entity.FnOrEntity -> when (entity.value) {
            in fns -> TilFunction(entity.value)
            else -> Literal(entity.value)
        }
    }

    private fun convertNumLiteral(entity: Entity.Number) = when {
        entity.value.all { it.isDigit() } -> Literal(entity.value, Builtins.Nu)
        else -> Literal(entity.value, Builtins.Eta)
    }

    private fun convertVarRef(varRef: VarRef): TilVariable = TilVariable(varRef.name)

    private fun convertDataType(type: DataType): Type = when (type) {
        is DataType.ClassType -> convertFnType(type)
        is DataType.Collection.List -> convertList(type)
        is DataType.Collection.Tuple -> convertTuple(type)
        is DataType.PrimitiveType -> convertPrimitiveType(type)
    }

    private fun convertList(list: DataType.Collection.List) =
        TilList(convertDataType(list.type))

    private fun convertTuple(tuple: DataType.Collection.Tuple) =
        TilTuple(convertDataType(tuple.type))

    // The original grammar grouped Any type among built-in primitives
    // This approach is fine from a grammar perspective, I, however, do not like
    // it from a programming perspective, and thus a slight change of grammar
    // may be desirable if I ever find the time
    private fun convertPrimitiveType(type: DataType.PrimitiveType) = when {
        type.isGenericType -> convertGenericType(type)
        // The following case handles aliases as well as already processed atomics
        type.name == "*" -> ConstructionType
        type.name in repo -> repo[type.name]!!
        else -> convertAtomicType(type)
    }

    private fun convertAtomicType(type: DataType.PrimitiveType) = AtomicType(shortName="", name=type.name)
        .apply { repo.process(this) }

    private val DataType.PrimitiveType.isGenericType
        get() = name.startsWith("Any") &&
                    name.asSequence().drop(3).all { it.isDigit() }

    private fun convertGenericType(type: DataType.PrimitiveType) = GenericType(
        type.name.drop(3).toInt()
    )

    private fun convertFnType(classType: DataType.ClassType) = FunctionType(
        classType.signature.map { convertDataType(it) }
    )

}
