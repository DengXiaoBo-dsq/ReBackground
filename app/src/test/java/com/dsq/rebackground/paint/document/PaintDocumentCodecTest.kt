package com.dsq.rebackground.paint.document

import com.dsq.rebackground.paint.core.PaintCommand
import com.dsq.rebackground.paint.core.PaintDocument
import com.dsq.rebackground.paint.core.PaintLayer
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.Stroke
import com.dsq.rebackground.paint.stroke.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PaintDocumentCodecTest {
    @Test
    fun document_round_trips_through_text_codec() {
        val document = PaintDocument(128, 64).also { it.addLayer(PaintLayer("ink", "墨线")) }
        document.apply(
            PaintCommand.AddStroke(
                "ink",
                Stroke(
                    listOf(
                        StrokePoint(Vec2(1f, 2f), .5f, 0f, 0f, 10),
                        StrokePoint(Vec2(3f, 4f), 1f, .2f, -.1f, 20)
                    )
                )
            )
        )

        val codec = PaintDocumentCodec()
        val restored = codec.decode(codec.encode(document))
        assertEquals(document.width, restored.width)
        assertEquals(document.height, restored.height)
        assertEquals(document.layers.single().commands.size, restored.layers.single().commands.size)
    }
}
