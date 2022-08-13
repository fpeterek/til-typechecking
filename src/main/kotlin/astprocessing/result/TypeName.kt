package org.fpeterek.tilscript.interpreter.astprocessing.result

import org.fpeterek.tilscript.interpreter.util.SrcPosition

class TypeName(val name: String, srcPos: SrcPosition) : IntermediateResult(srcPos) {

    constructor(symbol: Symbol) : this(symbol.symbol, symbol.position)

}
