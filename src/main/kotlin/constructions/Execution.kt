package org.fpeterek.til.typechecking.constructions

import org.fpeterek.til.typechecking.constructions.isexecutable.Executable
import org.fpeterek.til.typechecking.types.Type
import org.fpeterek.til.typechecking.types.Unknown

class Execution(
    val construction: Construction,
    val executionOrder: Int,
    constructedType: Type = Unknown,
    constructionType: Type = Unknown
) : Construction(constructedType, constructionType), Executable {

    init {
        if (executionOrder < 1 || executionOrder > 2) {
            throw RuntimeException("Execution order must be either 1 or 2")
        }
    }

    override fun toString() = "$executionOrder^$construction"

}
