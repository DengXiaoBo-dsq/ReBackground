package com.dsq.rebackground.model

import android.graphics.Bitmap

/**
 * 图层数据模型（用于 PSD 导出）
 */
data class LayerData(
    val name: String,
    val bitmap: Bitmap,
    val opacity: Float = 1f,
    val visible: Boolean = true,
    val width: Int = bitmap.width,
    val height: Int = bitmap.height
)