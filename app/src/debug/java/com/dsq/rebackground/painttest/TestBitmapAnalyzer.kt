package com.dsq.rebackground.painttest.metrics

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object TestBitmapAnalyzer {

    data class RoiMean(val r: Float, val g: Float, val b: Float, val a: Float, val n: Int)

    fun roiMean(bitmap: Bitmap, cx: Int, cy: Int, w: Int, h: Int): RoiMean {
        val x0 = (cx - w / 2).coerceIn(0, bitmap.width - 1)
        val y0 = (cy - h / 2).coerceIn(0, bitmap.height - 1)
        val x1 = (x0 + w).coerceAtMost(bitmap.width)
        val y1 = (y0 + h).coerceAtMost(bitmap.height)

        var sr = 0L; var sg = 0L; var sb = 0L; var sa = 0L; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = bitmap.getPixel(x, y)
            sa += (p ushr 24) and 0xFF
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
            n++
        }
        if (n == 0) return RoiMean(0f, 0f, 0f, 0f, 0)
        return RoiMean(
            r = sr.toFloat() / n,
            g = sg.toFloat() / n,
            b = sb.toFloat() / n,
            a = sa.toFloat() / n,
            n = n
        )
    }

    fun rgbMae(mean: RoiMean, target: IntArray): Float {
        val tr = target[0].toFloat()
        val tg = target[1].toFloat()
        val tb = target[2].toFloat()
        return (abs(mean.r - tr) + abs(mean.g - tg) + abs(mean.b - tb)) / 3f
    }

    fun luminance(mean: RoiMean): Float = 0.2126f * mean.r + 0.7152f * mean.g + 0.0722f * mean.b

    fun bitmapMae(a: Bitmap, b: Bitmap, roiX: Int, roiY: Int, roiW: Int, roiH: Int): Float {
        val x0 = roiX.coerceIn(0, minOf(a.width, b.width) - 1)
        val y0 = roiY.coerceIn(0, minOf(a.height, b.height) - 1)
        val x1 = (x0 + roiW).coerceAtMost(minOf(a.width, b.width))
        val y1 = (y0 + roiH).coerceAtMost(minOf(a.height, b.height))

        var sum = 0.0
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val pa = a.getPixel(x, y)
            val pb = b.getPixel(x, y)
            val ar = (pa shr 16) and 0xFF; val ag = (pa shr 8) and 0xFF; val ab = pa and 0xFF
            val br = (pb shr 16) and 0xFF; val bg = (pb shr 8) and 0xFF; val bb = pb and 0xFF
            sum += (abs(ar - br) + abs(ag - bg) + abs(ab - bb)) / 3.0
            n++
        }
        return if (n == 0) 0f else (sum / n).toFloat()
    }

    /** 沿 centerlineY 扫描，统计覆盖像素占比。 */
    fun centerlineCoverage(
        bitmap: Bitmap,
        centerlineY: Int,
        xStart: Int,
        xEnd: Int,
        coverageThreshold: Int,
        background: Int = 0xFFFFFFFF.toInt()
    ): Float {
        val br = (background shr 16) and 0xFF
        val bg = (background shr 8) and 0xFF
        val bb = background and 0xFF

        var total = 0
        var covered = 0
        for (x in xStart..xEnd) {
            if (x < 0 || x >= bitmap.width) continue
            if (centerlineY < 0 || centerlineY >= bitmap.height) continue
            val p = bitmap.getPixel(x, centerlineY)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val diff = (abs(r - br) + abs(g - bg) + abs(b - bb)) / 3
            if (diff >= coverageThreshold) covered++
            total++
        }
        return if (total == 0) 0f else covered.toFloat() / total
    }

    /** 用于 T6 的"意外长段"检查：统计连续未覆盖段长度 > maxGap 的个数。 */
    fun countLongGaps(
        bitmap: Bitmap,
        centerlineY: Int,
        xStart: Int,
        xEnd: Int,
        coverageThreshold: Int,
        maxGap: Int,
        background: Int = 0xFFFFFFFF.toInt()
    ): Int {
        val br = (background shr 16) and 0xFF
        val bg = (background shr 8) and 0xFF
        val bb = background and 0xFF

        var gap = 0
        var longGaps = 0
        for (x in xStart..xEnd) {
            if (x < 0 || x >= bitmap.width) continue
            val p = bitmap.getPixel(x, centerlineY)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val diff = (abs(r - br) + abs(g - bg) + abs(b - bb)) / 3
            if (diff < coverageThreshold) {
                gap++
            } else {
                if (gap > maxGap) longGaps++
                gap = 0
            }
        }
        if (gap > maxGap) longGaps++
        return longGaps
    }

    fun savePng(bitmap: Bitmap, path: String) {
        val f = java.io.File(path)
        f.parentFile?.mkdirs()
        f.outputStream().use { os ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
        }
    }
}