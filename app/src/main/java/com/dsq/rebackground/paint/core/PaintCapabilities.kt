package com.dsq.rebackground.paint.core

/** Device-independent engine capabilities. GPU-specific capabilities belong to a later renderer phase. */
data class PaintCapabilities(
    val supportsPressure: Boolean = true,
    val supportsTilt: Boolean = true,
    val maximumDocumentSize: Int = 4096
)
