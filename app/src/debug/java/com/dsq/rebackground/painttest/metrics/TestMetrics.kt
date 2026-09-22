package com.dsq.rebackground.painttest.metrics

import android.graphics.Bitmap
import com.dsq.rebackground.painttest.TestFixture

object TestMetrics {

    data class Outcome(
        val pass: Boolean,
        val failures: List<String>,
        val extras: Map<String, Any>
    )

    /**
     * @param id            fixture id
     * @param segmentMetrics  每 segment 的 PaintDebugMetrics.snapshot()
     * @param segmentBitmaps  每 segment 后的 composite bitmap
     * @param finalBitmap     最后一次 capture（= segmentBitmaps.last()，但显式传入）
     * @param lifecycle       累计 lifecycle 计数
     * @param fixture         用于读取 expected
     */
    fun evaluate(
        id: String,
        segmentMetrics: List<Map<String, Any>>,
        segmentBitmaps: List<Bitmap?>,
        finalBitmap: Bitmap?,
        lifecycle: Map<String, Int>,
        fixture: TestFixture
    ): Outcome {
        val failures = mutableListOf<String>()
        val extras = mutableMapOf<String, Any>()

        // 通用硬门槛：所有 segment 的 nan / inf / unexpectedOob
        segmentMetrics.forEachIndexed { i, m ->
            val nan = (m["nan"] as? Int) ?: 0
            val inf = (m["inf"] as? Int) ?: 0
            val uoob = (m["unexpectedOob"] as? Int) ?: 0
            val glError = (m["glError"] as? Number)?.toInt() ?: -1
            val frameTime = (m["frameTime"] as? Number)?.toFloat() ?: 0f
            val pts = (m["points"] as? Int) ?: 0
            if (nan != 0) failures += "seg$i.nan=$nan"
            if (inf != 0) failures += "seg$i.inf=$inf"
            if (uoob != 0) failures += "seg$i.unexpectedOob=$uoob"
            if (glError != 0) failures += "seg$i.glError=$glError"
            if (!frameTime.isFinite() || frameTime <= 0f) failures += "seg$i.frameTime=$frameTime"
            if (pts <= 0) failures += "seg$i.points=$pts"
        }

        when (id) {
            "T1", "T2", "T3" -> {
                val bmp = finalBitmap
                if (bmp == null) { failures += "no_bitmap"; return Outcome(false, failures, extras) }
                val roi = fixture.expected["roiX"]?.let { (it as Number).toInt() } ?: 0
                val roiY = fixture.expected["roiY"]?.let { (it as Number).toInt() } ?: 0
                val roiW = fixture.expected["roiW"]?.let { (it as Number).toInt() } ?: 20
                val roiH = fixture.expected["roiH"]?.let { (it as Number).toInt() } ?: 20
                val targetArr = fixture.expected["targetRgb"]
                val target = (targetArr as? List<*>)?.map { (it as Number).toInt() }?.toIntArray()
                    ?: intArrayOf(0,0,0)
                val maeMax = fixture.expected["rgbMaeMax"]?.let { (it as Number).toFloat() } ?: 2f

                val mean = TestBitmapAnalyzer.roiMean(bmp, roi, roiY, roiW, roiH)
                val mae = TestBitmapAnalyzer.rgbMae(mean, target)
                extras["roiMeanR"] = mean.r
                extras["roiMeanG"] = mean.g
                extras["roiMeanB"] = mean.b
                extras["rgbMae"] = mae
                extras["rgbMaeMax"] = maeMax
                if (mae > maeMax) failures += "rgbMae=$mae > $maeMax"
            }

            "T4" -> {
                if (segmentBitmaps.size < 3) { failures += "need_3_bitmaps"; return Outcome(false, failures, extras) }
                val roi = fixture.expected["roiX"]?.let { (it as Number).toInt() } ?: 0
                val roiY = fixture.expected["roiY"]?.let { (it as Number).toInt() } ?: 0
                val roiW = fixture.expected["roiW"]?.let { (it as Number).toInt() } ?: 20
                val roiH = fixture.expected["roiH"]?.let { (it as Number).toInt() } ?: 20
                val lum = segmentBitmaps.map { b ->
                    if (b == null) 255f else {
                        val mean = TestBitmapAnalyzer.roiMean(b, roi, roiY, roiW, roiH)
                        TestBitmapAnalyzer.luminance(mean)
                    }
                }
                extras["luminance"] = lum
                if (!(lum[0] > lum[1] && lum[1] > lum[2])) {
                    failures += "T4.not_monotonic lum=$lum"
                }
                val minDelta = fixture.expected["minDiffBetweenSegments"]
                    ?.let { (it as Number).toFloat() } ?: 0f
                val deltas = listOf(lum[0] - lum[1], lum[1] - lum[2])
                extras["luminanceDeltas"] = deltas
                extras["minDiffBetweenSegments"] = minDelta
                if (deltas.any { it < minDelta }) {
                    failures += "T4.delta=$deltas < $minDelta"
                }
            }

            "T5" -> {
                if (segmentBitmaps.size < 2) { failures += "need_2_bitmaps"; return Outcome(false, failures, extras) }
                val a = segmentBitmaps[0] ?: run { failures += "bitmap_a_null"; return Outcome(false, failures, extras) }
                val b = segmentBitmaps[1] ?: run { failures += "bitmap_b_null"; return Outcome(false, failures, extras) }
                val roi = fixture.expected["roiX"]?.let { (it as Number).toInt() } ?: 0
                val roiY = fixture.expected["roiY"]?.let { (it as Number).toInt() } ?: 0
                val roiW = fixture.expected["roiW"]?.let { (it as Number).toInt() } ?: 40
                val roiH = fixture.expected["roiH"]?.let { (it as Number).toInt() } ?: 40
                val maeMax = fixture.expected["idleMaeMax"]?.let { (it as Number).toFloat() } ?: 1f
                val mae = TestBitmapAnalyzer.bitmapMae(a, b, roi, roiY, roiW, roiH)
                extras["idleMae"] = mae
                extras["idleMaeMax"] = maeMax
                if (mae > maeMax) failures += "idleMae=$mae > $maeMax"
            }

            "T6" -> {
                val bmp = finalBitmap
                if (bmp == null) { failures += "no_bitmap"; return Outcome(false, failures, extras) }
                val y = fixture.expected["centerlineY"]?.let { (it as Number).toInt() } ?: 0
                val x0 = fixture.expected["centerlineXStart"]?.let { (it as Number).toInt() } ?: 0
                val x1 = fixture.expected["centerlineXEnd"]?.let { (it as Number).toInt() } ?: 0
                val th = fixture.expected["coverageThreshold"]?.let { (it as Number).toInt() } ?: 30
                val minCov = fixture.expected["minCenterlineCoverage"]?.let { (it as Number).toFloat() } ?: 0.95f
                val maxLongGapCount = fixture.expected["unexpectedLongSegmentCount"]?.let { (it as Number).toInt() } ?: 0
                val maxGapPixels = fixture.expected["maxGapPixels"]?.let { (it as Number).toInt() } ?: 5

                val cov = TestBitmapAnalyzer.centerlineCoverage(bmp, y, x0, x1, th)
                val longGaps = TestBitmapAnalyzer.countLongGaps(bmp, y, x0, x1, th, maxGapPixels)
                extras["centerlineCoverage"] = cov
                extras["minCenterlineCoverage"] = minCov
                extras["longGaps"] = longGaps
                extras["maxLongGaps"] = maxLongGapCount
                extras["maxGapPixels"] = maxGapPixels
                if (cov < minCov) failures += "coverage=$cov < $minCov"
                if (longGaps > maxLongGapCount) failures += "longGaps=$longGaps > $maxLongGapCount"
            }

            "T7" -> {
                val exp = fixture.expected["lifecycle"] as? Map<*, *>
                val expBegin = (exp?.get("begin") as? Number)?.toInt() ?: 0
                val expFinish = (exp?.get("finish") as? Number)?.toInt() ?: 0
                val expActive = (exp?.get("active") as? Number)?.toInt() ?: 0
                val expCancel = (exp?.get("cancel") as? Number)?.toInt() ?: 0
                extras["lifecycle"] = lifecycle
                if ((lifecycle["begin"] ?: -1) != expBegin) failures += "begin=${lifecycle["begin"]} != $expBegin"
                if ((lifecycle["finish"] ?: -1) != expFinish) failures += "finish=${lifecycle["finish"]} != $expFinish"
                if ((lifecycle["active"] ?: -1) != expActive) failures += "active=${lifecycle["active"]} != $expActive"
                if ((lifecycle["cancel"] ?: -1) != expCancel) failures += "cancel=${lifecycle["cancel"]} != $expCancel"
            }
        }

        return Outcome(failures.isEmpty(), failures, extras)
    }
}
