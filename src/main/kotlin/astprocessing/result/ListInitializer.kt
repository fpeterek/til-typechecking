package org.fpeterek.tilscript.interpreter.astprocessing.result

import org.fpeterek.tilscript.interpreter.util.SrcPosition

class ListInitializer(
    val values: List<Construction>,
    srcPos: SrcPosition
) : Construction(srcPos)
