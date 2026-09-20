package com.dsq.rebackground.paint.core

/** P0 facade. Rendering is deliberately not coupled to document and stroke semantics. */
class PaintEngine(val capabilities: PaintCapabilities = PaintCapabilities()) {
    fun open(document: PaintDocument): PaintSession {
        require(document.width <= capabilities.maximumDocumentSize && document.height <= capabilities.maximumDocumentSize) {
            "Document exceeds this engine's configured size limit"
        }
        return PaintSession(document)
    }
}
