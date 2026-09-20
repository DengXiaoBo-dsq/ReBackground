package com.dsq.rebackground.paint.core

/** Document dimensions define the sole coordinate system persisted by the paint engine. */
class PaintDocument(val width: Int, val height: Int, layers: List<PaintLayer> = emptyList()) {
    init { require(width > 0 && height > 0) { "Document dimensions must be positive" } }

    private val mutableLayers = layers.toMutableList()
    val layers: List<PaintLayer> get() = mutableLayers

    fun addLayer(layer: PaintLayer) {
        require(mutableLayers.none { it.id == layer.id }) { "Layer id already exists: ${layer.id}" }
        mutableLayers += layer
    }

    fun removeLayer(layer: PaintLayer) {
        require(mutableLayers.size > 1) { "At least one layer is required" }
        check(mutableLayers.remove(layer)) { "Layer not found: ${layer.id}" }
    }

    fun layer(id: String): PaintLayer? = mutableLayers.firstOrNull { it.id == id }

    fun apply(command: PaintCommand) {
        requireNotNull(layer(command.layerId)) { "Unknown layer: ${command.layerId}" }.append(command)
    }

    fun removeCommand(command: PaintCommand) {
        requireNotNull(layer(command.layerId)) { "Unknown layer: ${command.layerId}" }.remove(command)
    }

    fun copy(): PaintDocument {
        val result = PaintDocument(width, height)
        layers.forEach { source ->
            val target = PaintLayer(source.id, source.name, source.visible)
            source.commands.forEach { target.append(it) }
            result.addLayer(target)
        }
        return result
    }

    fun replaceWith(other: PaintDocument) {
        require(width == other.width && height == other.height) {
            "Document dimensions must match when restoring a snapshot"
        }
        mutableLayers.clear()
        other.layers.forEach { source ->
            val target = PaintLayer(source.id, source.name, source.visible)
            source.commands.forEach { target.append(it) }
            mutableLayers += target
        }
    }
}
