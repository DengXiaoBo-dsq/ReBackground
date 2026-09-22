package com.dsq.rebackground.painttest

import android.graphics.Bitmap
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.StrokePoint
import com.dsq.rebackground.paint.ui.PaintEngineController
import com.dsq.rebackground.painttest.metrics.TestMetrics

class PaintTestRunner(private val controller: PaintEngineController) {

    fun runFixture(
        fixture: TestFixture,
        onComplete: (TestResult) -> Unit
    ) {
        // Every fixture is an independent deterministic replay.  In particular,
        // pigment accumulation from T(n) must never become input to T(n+1).
        controller.clearCanvasForTest()
        controller.resetLifecycleCountsForTest()

        val segMetrics = mutableListOf<Map<String, Any>>()
        val segBitmaps = mutableListOf<Bitmap?>()

        fun nextSegment(i: Int) {
            if (i >= fixture.segments.size) {
                finish(fixture, segMetrics, segBitmaps, onComplete)
                return
            }
            val seg = fixture.segments[i]
            val points = ArrayList<StrokePoint>(seg.points.size)
            var t = 0L
            for (p in seg.points) {
                points += StrokePoint(
                    position = Vec2(p.x, p.y),
                    pressure = p.pressure.coerceIn(0f, 1f),
                    tiltX = p.tiltX,
                    tiltY = p.tiltY,
                    timestampMillis = t
                )
                t += fixture.timestampStepMs
            }

            val m = controller.replayStrokeForTest(
                points = points,
                brushId = "",
                color = seg.colorHex,
                opacity = seg.opacity,
                flow = seg.flow,
                seed = fixture.seed
            )
            segMetrics += m

            controller.captureCompositeForTest { bmp, renderMetrics ->
                segMetrics[segMetrics.lastIndex] = m + renderMetrics
                segBitmaps += bmp
                nextSegment(i + 1)
            }
        }

        // T5 特殊：只跑一个 segment，然后 sleep + 再 capture
        if (fixture.id == "T5") {
            runT5(fixture, segMetrics, segBitmaps, onComplete)
            return
        }

        nextSegment(0)
    }

    private fun runT5(
        fixture: TestFixture,
        segMetrics: MutableList<Map<String, Any>>,
        segBitmaps: MutableList<Bitmap?>,
        onComplete: (TestResult) -> Unit
    ) {
        val seg = fixture.segments[0]
        val points = ArrayList<StrokePoint>(seg.points.size)
        var t = 0L
        for (p in seg.points) {
            points += StrokePoint(
                position = Vec2(p.x, p.y),
                pressure = p.pressure.coerceIn(0f, 1f),
                tiltX = p.tiltX, tiltY = p.tiltY,
                timestampMillis = t
            )
            t += fixture.timestampStepMs
        }
        val m = controller.replayStrokeForTest(
            points = points, brushId = "", color = seg.colorHex,
            opacity = seg.opacity, flow = seg.flow, seed = fixture.seed
        )
        segMetrics += m

        controller.captureCompositeForTest { bmpA, renderMetrics ->
            segMetrics[segMetrics.lastIndex] = m + renderMetrics
            segBitmaps += bmpA
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                controller.captureCompositeForTest { bmpB, _ ->
                    segBitmaps += bmpB
                    finish(fixture, segMetrics, segBitmaps, onComplete)
                }
            }, fixture.idleWaitMs)
        }
    }

    fun runFixtureWithBitmaps(
        fixture: TestFixture,
        onComplete: (TestResult, List<Bitmap?>) -> Unit
    ) {
        runFixture(fixture) { result ->
            onComplete(result, lastBitmaps)
        }
    }

    private var lastBitmaps: List<Bitmap?> = emptyList()

    private fun finish(
        fixture: TestFixture,
        segMetrics: List<Map<String, Any>>,
        segBitmaps: List<Bitmap?>,
        onComplete: (TestResult) -> Unit
    ) {
        val lifecycle = controller.lifecycleCountsForTest()
        val finalBmp = segBitmaps.lastOrNull()

        val outcome = TestMetrics.evaluate(
            id = fixture.id,
            segmentMetrics = segMetrics,
            segmentBitmaps = segBitmaps,
            finalBitmap = finalBmp,
            lifecycle = lifecycle,
            fixture = fixture
        )

        val result = TestResult(
            fixtureId = fixture.id,
            pass = outcome.pass,
            failures = outcome.failures,
            segmentMetrics = segMetrics,
            lifecycle = lifecycle,
            extras = outcome.extras,
            segmentBitmapPaths = emptyList(),
            finalBitmapPath = null
        )
        // Keep an immutable copy for EvidenceWriter.  Without this assignment the
        // test result may pass while its required PNG evidence is always empty.
        lastBitmaps = segBitmaps.toList()
        onComplete(result)
    }
}
