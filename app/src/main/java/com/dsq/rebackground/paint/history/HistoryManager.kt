package com.dsq.rebackground.paint.history

import com.dsq.rebackground.paint.core.PaintCommand
import com.dsq.rebackground.paint.core.PaintDocument

/** Command-level undo/redo. It does not own a document; it mutates the supplied document. */
class HistoryManager(
    private val document: PaintDocument,
    private val limit: Int = 128
) {
    init {
        require(limit > 0)
    }

    private val undoStack = ArrayDeque<PaintCommand>()
    private val redoStack = ArrayDeque<PaintCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun apply(command: PaintCommand) {
        document.apply(command)
        pushUndo(command)
        redoStack.clear()
    }

    fun undo(): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        document.removeCommand(command)
        redoStack.addLast(command)
        return true
    }

    fun redo(): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        document.apply(command)
        pushUndo(command)
        return true
    }

    private fun pushUndo(command: PaintCommand) {
        undoStack.addLast(command)
        if (undoStack.size > limit) undoStack.removeFirst()
    }
}
