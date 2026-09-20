package com.dsq.rebackground.paint.core

/** A document layer stores semantic commands; rendering is owned by later renderer stages. */
class PaintLayer(val id: String, var name: String = id, var visible: Boolean = true) {
    private val mutableCommands = mutableListOf<PaintCommand>()
    val commands: List<PaintCommand> get() = mutableCommands

    internal fun append(command: PaintCommand) {
        require(command.layerId == id) { "A command must be appended to its own layer" }
        mutableCommands += command
    }

    internal fun remove(command: PaintCommand) {
        check(mutableCommands.remove(command)) { "Command not found in layer $id" }
    }
}
