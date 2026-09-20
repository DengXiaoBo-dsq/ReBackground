package com.dsq.rebackground.paint.history

import com.dsq.rebackground.paint.core.PaintDocument

/** Stores bounded deep snapshots for coarse-grained recovery. */
class CheckpointManager(private val limit: Int = 16) {
    init {
        require(limit > 0)
    }

    private val checkpoints = ArrayDeque<PaintDocument>()
    val count: Int get() = checkpoints.size

    fun checkpoint(document: PaintDocument) {
        checkpoints.addLast(document.copy())
        if (checkpoints.size > limit) checkpoints.removeFirst()
    }

    fun restoreLatest(document: PaintDocument): Boolean {
        val snapshot = checkpoints.removeLastOrNull() ?: return false
        document.replaceWith(snapshot.copy())
        return true
    }

    fun clear() {
        checkpoints.clear()
    }
}
