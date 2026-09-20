package com.dsq.rebackground.paint.core

import com.dsq.rebackground.paint.brush.BrushDefinition
import com.dsq.rebackground.paint.pigment.PigmentColor
import com.dsq.rebackground.paint.stroke.Stroke

/** Semantic document change. It intentionally contains no Bitmap or Canvas state. */
sealed interface PaintCommand {
    val layerId: String

    data class AddStroke(
        override val layerId: String,
        val stroke: Stroke,
        val brush: BrushDefinition? = null,
        val color: PigmentColor? = null
    ) : PaintCommand
}
