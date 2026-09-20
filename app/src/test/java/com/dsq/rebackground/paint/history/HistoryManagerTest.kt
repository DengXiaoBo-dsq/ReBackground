package com.dsq.rebackground.paint.history

import com.dsq.rebackground.paint.core.PaintCommand
import com.dsq.rebackground.paint.core.PaintDocument
import com.dsq.rebackground.paint.core.PaintLayer
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.Stroke
import com.dsq.rebackground.paint.stroke.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryManagerTest {
    private fun stroke(x: Float): Stroke = Stroke(
        listOf(StrokePoint(Vec2(x, 0f), 1f, 0f, 0f, 0))
    )

    @Test
    fun undo_and_redo_restore_commands() {
        val document = PaintDocument(100, 100).also { it.addLayer(PaintLayer("ink")) }
        val history = HistoryManager(document)
        val command = PaintCommand.AddStroke("ink", stroke(1f))

        history.apply(command)
        assertTrue(history.canUndo)
        assertEquals(1, document.layers.single().commands.size)

        assertTrue(history.undo())
        assertTrue(document.layers.single().commands.isEmpty())
        assertFalse(history.canUndo)

        assertTrue(history.redo())
        assertEquals(1, document.layers.single().commands.size)
    }

    @Test
    fun checkpoint_restores_previous_document_state() {
        val document = PaintDocument(100, 100).also { it.addLayer(PaintLayer("ink")) }
        val checkpoints = CheckpointManager()
        val history = HistoryManager(document)

        history.apply(PaintCommand.AddStroke("ink", stroke(1f)))
        checkpoints.checkpoint(document)
        history.apply(PaintCommand.AddStroke("ink", stroke(2f)))

        assertTrue(checkpoints.restoreLatest(document))
        assertEquals(1, document.layers.single().commands.size)
    }
}
