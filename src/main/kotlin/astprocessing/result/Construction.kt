package org.fpeterek.til.typechecking.astprocessing.result

sealed class Construction : IntermediateResult() {

    fun extensionalize() = Composition(
        Composition(
            this,
            listOf(VarRef("w"))
        ),
        listOf(VarRef("t"))
    )

    class VarRef(val name: String) : Construction() {
        constructor(varName: VarName) : this(varName.name)
    }

    class Closure(val vars: List<TypedVar>, val construction: IntermediateResult) :
        Construction() {
        constructor(vars: TypedVars, construction: IntermediateResult) :
                this(vars.vars, construction)
    }

    class Execution(val order: Int, val construction: IntermediateResult) :
        Construction()

    class Composition(val fn: IntermediateResult, val args: List<IntermediateResult>) :
        Construction()
}