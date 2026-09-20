package com.dsq.rebackground.paint.document

import com.dsq.rebackground.paint.core.PaintCommand
import com.dsq.rebackground.paint.core.PaintDocument
import com.dsq.rebackground.paint.core.PaintLayer
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.Stroke
import com.dsq.rebackground.paint.stroke.StrokePoint

/** Small text codec for the current semantic document model. Future command types extend this format. */
class PaintDocumentCodec {
    fun encode(document: PaintDocument): String = buildString {
        appendLine("v1")
        appendLine("W ${document.width} ${document.height}")
        document.layers.forEach { layer ->
            appendLine("L ${escape(layer.id)} ${escape(layer.name)} ${layer.visible}")
            layer.commands.filterIsInstance<PaintCommand.AddStroke>().forEach { command ->
                appendLine("S ${escape(command.layerId)} ${command.stroke.points.size}")
                command.stroke.points.forEach { point ->
                    appendLine(
                        "P ${point.position.x} ${point.position.y} ${point.pressure} " +
                            "${point.tiltX} ${point.tiltY} ${point.timestampMillis}"
                    )
                }
            }
        }
    }

    fun decode(text: String): PaintDocument {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(lines.firstOrNull() == "v1") { "Unsupported document format" }

        var document: PaintDocument? = null
        var currentLayerId: String? = null
        var remainingStrokePoints = 0
        val strokePoints = ArrayList<StrokePoint>()

        for (line in lines.drop(1)) {
            when {
                line.startsWith("W ") -> {
                    val parts = line.split(' ')
                    require(parts.size == 3) { "Invalid document dimension line" }
                    val width = parts[1].toIntOrNull() ?: error("Invalid width")
                    val height = parts[2].toIntOrNull() ?: error("Invalid height")
                    document = PaintDocument(width, height)
                }
                line.startsWith("L ") -> {
                    val doc = requireNotNull(document) { "Layer appears before document dimensions" }
                    val parts = line.split(' ')
                    require(parts.size == 4) { "Invalid layer line" }
                    val layer = PaintLayer(unescape(parts[1]), unescape(parts[2]), parts[3].toBoolean())
                    doc.addLayer(layer)
                    currentLayerId = layer.id
                }
                line.startsWith("S ") -> {
                    requireNotNull(document) { "Stroke appears before document dimensions" }
                    val parts = line.split(' ')
                    require(parts.size == 3) { "Invalid stroke line" }
                    currentLayerId = unescape(parts[1])
                    remainingStrokePoints = parts[2].toIntOrNull() ?: error("Invalid stroke point count")
                    strokePoints.clear()
                }
                line.startsWith("P ") -> {
                    val doc = requireNotNull(document) { "Point appears before document dimensions" }
                    require(remainingStrokePoints > 0) { "Unexpected point line" }
                    val parts = line.split(' ')
                    require(parts.size == 7) { "Invalid point line" }
                    strokePoints += StrokePoint(
                        position = Vec2(parts[1].toFloat(), parts[2].toFloat()),
                        pressure = parts[3].toFloat(),
                        tiltX = parts[4].toFloat(),
                        tiltY = parts[5].toFloat(),
                        timestampMillis = parts[6].toLong()
                    )
                    remainingStrokePoints--
                    if (remainingStrokePoints == 0) {
                        val layerId = requireNotNull(currentLayerId) { "Missing stroke layer id" }
                        doc.apply(PaintCommand.AddStroke(layerId, Stroke(strokePoints.toList())))
                    }
                }
            }
        }

        require(document != null) { "Document dimensions are missing" }
        require(remainingStrokePoints == 0) { "Document ended inside a stroke" }
        return document!!
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(" ", "\\s")
        .replace("\n", "\\n")

    private fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\s", " ")
        .replace("\\\\", "\\")
}
