package com.dsq.rebackground.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Bitmap.Config.ARGB_8888

/**
 * 纹理优化工具
 * 对画笔纹理进行预处理，提升渲染质量
 */
object TextureOptimizer {

    /**
     * 羽化纹理边缘，消除锯齿
     *
     * 原理：对纹理边缘的像素进行透明度渐变，使边缘更柔和
     *
     * @param bitmap 原始纹理
     * @param radius 羽化半径（像素）
     * @return 羽化后的纹理
     */
    fun featherTexture(bitmap: Bitmap, radius: Int = 3): Bitmap {
        val result = bitmap.copy(ARGB_8888, true)
        val width = result.width
        val height = result.height

        // 先获取纹理的形状边界（非透明像素）
        val shapeBounds = getShapeBounds(result)
        if (shapeBounds == null) return result

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = result.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha == 0) continue

                // 计算到形状边界的距离
                val distToEdge = distanceToEdge(x, y, shapeBounds, result)

                // 如果在边缘附近，进行透明度渐变
                if (distToEdge < radius) {
                    val factor = (distToEdge.toFloat() / radius).coerceIn(0f, 1f)
                    val newAlpha = (alpha * factor).toInt()
                    result.setPixel(x, y, Color.argb(newAlpha, Color.red(pixel), Color.green(pixel), Color.blue(pixel)))
                }
            }
        }
        return result
    }

    /**
     * 获取形状边界（非透明区域的最小/最大坐标）
     */
    private fun getShapeBounds(bitmap: Bitmap): Rect? {
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = 0
        var maxY = 0
        var found = false

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 0) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                    found = true
                }
            }
        }
        return if (found) Rect(minX, minY, maxX, maxY) else null
    }

    /**
     * 计算点到边界的距离（简化版）
     */
    private fun distanceToEdge(x: Int, y: Int, bounds: Rect, bitmap: Bitmap): Float {
        // 到矩形边界的距离
        val dx = minOf(
            if (x < bounds.left) bounds.left - x else 0,
            if (x > bounds.right) x - bounds.right else 0
        )
        val dy = minOf(
            if (y < bounds.top) bounds.top - y else 0,
            if (y > bounds.bottom) y - bounds.bottom else 0
        )
        return kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
    }

    /**
     * 确保纹理背景透明（如果纹理有白色/黑色背景，转换为透明）
     */
    fun ensureTransparentBackground(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(ARGB_8888, true)

        // 检测角部颜色作为背景色
        val corners = listOf(
            result.getPixel(0, 0),
            result.getPixel(result.width - 1, 0),
            result.getPixel(0, result.height - 1),
            result.getPixel(result.width - 1, result.height - 1)
        )

        // 如果四个角颜色相同，视为背景色
        if (corners.distinct().size == 1) {
            val bgColor = corners[0]
            for (x in 0 until result.width) {
                for (y in 0 until result.height) {
                    if (result.getPixel(x, y) == bgColor) {
                        result.setPixel(x, y, Color.TRANSPARENT)
                    }
                }
            }
        }
        return result
    }

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)
}