package org.fpeterek.til.typechecking.types


class GenericType(val argNumber: Int) : Type() {

    override val name
        get() = "Any<$argNumber>"

    override fun toString() = name

}
