package com.dsq.rebackground.paint.core

/** Mutable lifetime owner for one open document. */
class PaintSession(val document: PaintDocument) {
    fun apply(command: PaintCommand) = document.apply(command)
}
