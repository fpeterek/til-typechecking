package org.fpeterek.til.typechecking.sentence

import org.fpeterek.til.typechecking.sentence.isexecutable.NonExecutable
import org.fpeterek.til.typechecking.types.*


class TilFunction(
    val name: String,
    type: Type = Unknown,
) : Construction(constructedType=type, constructionType=ConstructionType),
    NonExecutable {

    override fun toString() = name

    val fullyTyped: Boolean
        get() = constructedType is FunctionType && constructedType.fullyTyped

    init {
        if (type !is Unknown && type !is FunctionType) {
            throw RuntimeException("Type of TilFunction must be Unknown or FunctionType")
        }
    }

}
