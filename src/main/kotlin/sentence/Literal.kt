package org.fpeterek.til.typechecking.sentence

import org.fpeterek.til.typechecking.sentence.isexecutable.NonExecutable
import org.fpeterek.til.typechecking.types.AtomicType
import org.fpeterek.til.typechecking.types.Type
import org.fpeterek.til.typechecking.types.ConstructionType
import org.fpeterek.til.typechecking.types.Unknown

class Literal(
    val value: String,
    type: Type = Unknown
) : Construction(constructedType=type, constructionType=ConstructionType), NonExecutable {

    init {
        when (type) {
            is AtomicType, Unknown -> Unit
            else -> throw RuntimeException("Literal type must be either AtomicType or Unknown")
        }
    }

    override fun toString() = value

}