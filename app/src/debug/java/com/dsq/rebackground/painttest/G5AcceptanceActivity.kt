package com.dsq.rebackground.painttest

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.dsq.rebackground.paint.brush.BrushStamp
import com.dsq.rebackground.paint.material.DryBrushLoad
import com.dsq.rebackground.paint.material.DryBrushLoadConfig
import com.dsq.rebackground.paint.material.DryDeposit
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.paper.PaperHeightField
import com.dsq.rebackground.paint.paper.PaperPresets
import com.dsq.rebackground.paint.rendering.gl.ColoredBrushStamp
import com.dsq.rebackground.paint.rendering.gl.PaintGLSurfaceView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Device-side G5 acceptance: CPU invariants plus real GLES paper/bristle output. */
class G5AcceptanceActivity : AppCompatActivity() {
    private lateinit var surface: PaintGLSurfaceView
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = PaintGLSurfaceView(this)
        setContentView(surface)
        surface.setDebugGlReadyListener {
            if (!started) {
                started = true
                runOnUiThread {
                    when (intent.getStringExtra("gate")?.uppercase(Locale.US) ?: "A") {
                        "A" -> runLoad()
                        "B" -> runDepositBudget()
                        "C" -> runPaperDifference(false)
                        "D" -> runPaperDifference(true)
                        "E" -> runDryGaps()
                        "F" -> runBristleOrientation()
                        "R" -> runPaperRenderAcceptance()
                        else -> finishResult("?", false, JSONObject().put("error", "unknown gate"))
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); surface.onResume() }
    override fun onPause() { surface.onPause(); super.onPause() }

    private fun runLoad() {
        val checkpoints = listOf(0f, 100f, 250f, 500f, 750f, 1000f)
        val config = DryBrushLoadConfig(initialLoad = 1f, depletionRatePerDocumentUnit = DEPLETION)
        val values = checkpoints.map { DryBrushLoad.advance(1f, it, 0f, config).toDouble() }
        val monotonic = values.zipWithNext().all { (a, b) -> b <= a + 1e-7 }
        val bounded = values.all { it in 0.0..1.0 }
        val exponentialR2 = linearR2(checkpoints.map(Float::toDouble), values.map(::ln))
        val rows = JSONArray(checkpoints.indices.map { index ->
            JSONObject().put("distancePx", checkpoints[index]).put("load", values[index])
        })
        finishResult("A", bounded && monotonic && exponentialR2 >= 0.98, JSONObject()
            .put("checkpoints", rows).put("bounded", bounded).put("nonIncreasing", monotonic)
            .put("exponentialR2", exponentialR2))
    }

    private fun runDepositBudget() {
        val loads = (0..100).map { 1.0 - it / 100.0 }
        val masses: List<Double> = loads.map {
            DryDeposit(0.73f, it.toFloat(), 0.82f, 0.67f).mass.toDouble()
        }
        val corr = correlation(loads, masses)
        val falling = masses.zipWithNext().all { (a, b) -> b <= a + 1e-9 }
        finishResult("B", corr >= 0.8 && falling, JSONObject()
            .put("correlationLoadDepositMass", corr).put("massFallsWithLoad", falling)
            .put("massAtFullLoad", masses.first()).put("massAtEmptyLoad", masses.last()))
    }

    private fun runPaperDifference(correlationGate: Boolean) {
        val gate = if (correlationGate) "D" else "C"
        val canvas = 160
        val root = evidenceRoot(gate)
        val stamp = BrushStamp(
            center = Vec2(canvas / 2f, canvas / 2f), diameterDocumentUnits = 132f,
            aspectRatio = 1f, rotationRadians = 0f, dryLoad = 1f,
            bristleDensity = 0f, paperGrainAffinity = 1f, dryPressure = 0.5f,
        )
        surface.setDocumentSize(canvas, canvas)
        renderPaper(stamp, canvas, 0f) lowRender@{ low, lowMetrics ->
            if (low == null) {
                finishResult(gate, false, JSONObject().put("error", "low paper capture failed")); return@lowRender
            }
            saveFloatPng(low, canvas, canvas, File(root, "low-tooth.png"))
            renderPaper(stamp, canvas, 1f) highRender@{ high, highMetrics ->
                if (high == null) {
                    finishResult(gate, false, JSONObject().put("error", "high paper capture failed")); return@highRender
                }
                saveFloatPng(high, canvas, canvas, File(root, "high-tooth.png"))
                val lowCoverage = mutableListOf<Double>()
                val highCoverage = mutableListOf<Double>()
                val heights = mutableListOf<Double>()
                val field = PaperHeightField(PAPER_SEED)
                val radius = 48f
                for (y in 0 until canvas) for (x in 0 until canvas) {
                    val dx = x + 0.5f - canvas / 2f; val dy = y + 0.5f - canvas / 2f
                    if (dx * dx + dy * dy > radius * radius) continue
                    lowCoverage += alpha(low, canvas, x, y)
                    highCoverage += alpha(high, canvas, x, y)
                    heights += field.sample(x + 0.5f, y + 0.5f).toDouble()
                }
                val meanDifference = lowCoverage.indices.map { abs(lowCoverage[it] - highCoverage[it]) }.average()
                val varianceRatio = variance(highCoverage) / maxOf(variance(lowCoverage), 1e-12)
                val paperCorrelation = correlation(heights, highCoverage)
                val glErrorLow = (lowMetrics["glError"] as? Number)?.toInt() ?: -1
                val glErrorHigh = (highMetrics["glError"] as? Number)?.toInt() ?: -1
                val metrics = JSONObject().put("meanAbsCoverageDifference", meanDifference)
                    .put("paperVarianceRatio", varianceRatio).put("paperHeightCoverageCorrelation", paperCorrelation)
                    .put("samples", heights.size).put("lowGlError", glErrorLow).put("highGlError", glErrorHigh)
                val pass = if (correlationGate) abs(paperCorrelation) >= 0.5
                    else meanDifference >= 0.05 && varianceRatio >= 1.20
                finishResult(gate, pass && glErrorLow == 0 && glErrorHigh == 0, metrics)
            }
        }
    }

    private fun runDryGaps() {
        val width = 1100; val height = 128; val startX = 50f; val endX = 1050f
        val centerY = 64f; val diameter = 32f; val spacing = diameter * 0.15f
        val count = kotlin.math.ceil((endX - startX) / spacing).toInt()
        val stamps = (0..count).map { index ->
            val arc = index * (endX - startX) / count.toFloat()
            dryStamp(Vec2(startX + arc, centerY), diameter, 0f, arc)
        }
        val root = evidenceRoot("E")
        surface.setDocumentSize(width, height)
        surface.setDryPaper(0.65f, 1f, PAPER_SEED)
        render(stamps, width, height) { values, renderMetrics ->
            var gaps = 0; var current = 0; var maxGap = 0; var runs = 0
            if (values != null) {
                for (x in startX.toInt()..endX.toInt()) {
                    if (alpha(values, width, x, centerY.toInt()) < 0.08) {
                        gaps++; current++
                    } else if (current > 0) {
                        maxGap = maxOf(maxGap, current); runs++; current = 0
                    }
                }
                if (current > 0) { maxGap = maxOf(maxGap, current); runs++ }
                saveFloatPng(values, width, height, File(root, "dry-1000px.png"))
            }
            val gapRate = gaps / 1001.0
            val meanGap = if (runs == 0) 0.0 else gaps.toDouble() / runs
            val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
            finishResult("E", values != null && gapRate in 0.05..0.35 && glError == 0, JSONObject()
                .put("gapRate", gapRate).put("gapPixels", gaps).put("meanGapPx", meanGap)
                .put("maxGapPx", maxGap).put("gapRuns", runs).put("glError", glError))
        }
    }

    private fun runBristleOrientation() {
        // 44 dense bundles need at least four framebuffer pixels per bundle for
        // a structural-tensor direction estimate; the former 140px fixture was
        // sampling the pixel lattice rather than the bristle field.
        val canvas = 360
        val angles = (0 until 180 step 15).toList()
        val errors = mutableListOf<Double>()
        val rows = JSONArray()
        val root = evidenceRoot("F")
        surface.setDocumentSize(canvas, canvas)
        surface.setDryPaper(0f, 1f, PAPER_SEED)
        fun next(index: Int) {
            if (index == angles.size) {
                val sorted = errors.sorted()
                val mean = errors.average()
                val p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.lastIndex)]
                finishResult("F", mean <= 5.0 && p99 <= 12.0, JSONObject()
                    .put("meanOrientationErrorDegrees", mean).put("p99OrientationErrorDegrees", p99)
                    .put("cases", rows))
                return
            }
            val degrees = angles[index]
            val radians = Math.toRadians(degrees.toDouble()).toFloat()
            val stamp = dryStamp(Vec2(canvas / 2f, canvas / 2f), 300f, radians, 40f)
                .copy(dryLoad = 1f, paperGrainAffinity = 0f)
            render(listOf(stamp), canvas, canvas) { values, metrics ->
                val measured = values?.let {
                    estimateGradientOrientation(it, canvas, canvas / 2f, canvas / 2f, 100f)
                } ?: Double.NaN
                val expectedGradient = radians + PI / 2.0
                val error = if (measured.isFinite()) orientationErrorDegrees(measured, expectedGradient) else 180.0
                errors += error
                val glError = (metrics["glError"] as? Number)?.toInt() ?: -1
                rows.put(JSONObject().put("brushDegrees", degrees)
                    .put("measuredGradientDegrees", Math.toDegrees(measured)).put("errorDegrees", error)
                    .put("glError", glError))
                values?.let { saveFloatPng(it, canvas, canvas, File(root, "bristles-$degrees.png")) }
                next(index + 1)
            }
        }
        next(0)
    }

    /**
     * G5-R1..R5: one fixed pencil fixture rendered through the real brush FBO.
     * The fixture isolates canvas paper by disabling bristle bands while retaining
     * full paper affinity; this makes paper variance measurable and reproducible.
     */
    private fun runPaperRenderAcceptance() {
        val width = 512
        val height = 192
        val centerY = height / 2f
        val root = evidenceRoot("R")
        val pencil = (6..506 step 4).map { x ->
            BrushStamp(
                center = Vec2(x.toFloat(), centerY), diameterDocumentUnits = 26f,
                aspectRatio = 1f, rotationRadians = 0f, dryLoad = .85f,
                dryArcLengthDocumentUnits = (x - 6).toFloat(), bristleDensity = 0f,
                paperGrainAffinity = 1f, bristleSeed = 41, dryPressure = .48f,
            )
        }
        val papers = PaperPresets.all()
        val captures = LinkedHashMap<String, FloatArray>()
        val renderRows = JSONArray()
        surface.setDocumentSize(width, height)

        fun next(index: Int) {
            if (index == papers.size) {
                val smooth = captures.getValue(PaperPresets.SMOOTH_ID)
                val medium = captures.getValue(PaperPresets.MEDIUM_ID)
                val rough = captures.getValue(PaperPresets.ROUGH_WATERCOLOR_ID)
                val smoothCoverage = coverageRegion(smooth, width, height)
                val mediumCoverage = coverageRegion(medium, width, height)
                val roughCoverage = coverageRegion(rough, width, height)
                val smoothStd = standardDeviation(smoothCoverage)
                val mediumStd = standardDeviation(mediumCoverage)
                val roughStd = standardDeviation(roughCoverage)
                val stdRatio = roughStd / maxOf(smoothStd, 1e-9)
                val ssim = ssim(smoothCoverage, roughCoverage)
                val r1 = papers.all { paper ->
                    paper.material.roughness in 0f..1f &&
                        paper.material.absorption in 0f..1f &&
                        paper.material.fiberDensity in 0f..1f
                }
                val r2 = stdRatio >= 1.5
                val r3 = ssim < .95
                val r4 = roughStd >= mediumStd && mediumStd >= smoothStd && roughStd >= 1.5 * smoothStd
                val noGlErrors = (0 until renderRows.length()).all { renderRows.getJSONObject(it).getInt("glError") == 0 }
                val metrics = JSONObject()
                    .put("fixture", "pencil-500px-line")
                    .put("R1_parameterPath", JSONObject().put("roughness", r1).put("absorption", r1).put("fiberDensity", r1))
                    .put("R2_roughSmoothStdRatio", stdRatio)
                    .put("R3_smoothRoughSsim", ssim)
                    .put("R4_coverageVariance", JSONObject()
                        .put("smooth", smoothStd * smoothStd)
                        .put("medium", mediumStd * mediumStd)
                        .put("rough", roughStd * roughStd))
                    .put("R4_coverageStd", JSONObject().put("smooth", smoothStd).put("medium", mediumStd).put("rough", roughStd))
                    .put("renderCases", renderRows)
                    .put("noGlErrors", noGlErrors)
                finishResult("R", r1 && r2 && r3 && r4 && noGlErrors, metrics)
                return
            }
            val paper = papers[index]
            surface.setPaper(paper)
            render(pencil, width, height) { values, renderMetrics ->
                val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
                if (values == null) {
                    finishResult("R", false, JSONObject().put("error", "capture failed for ${paper.id}"))
                    return@render
                }
                captures[paper.id] = values
                saveFloatPng(values, width, height, File(root, "${paper.id}-pencil-500px.png"))
                renderRows.put(JSONObject().put("paperId", paper.id)
                    .put("roughness", paper.material.roughness)
                    .put("absorption", paper.material.absorption)
                    .put("fiberDensity", paper.material.fiberDensity)
                    .put("glError", glError))
                next(index + 1)
            }
        }
        next(0)
    }

    private fun dryStamp(center: Vec2, diameter: Float, rotation: Float, arc: Float) = BrushStamp(
        center = center, diameterDocumentUnits = diameter, aspectRatio = 1f, rotationRadians = rotation,
        dryLoad = exp(-DEPLETION * arc), dryArcLengthDocumentUnits = arc,
        bristleDensity = 0.9f, paperGrainAffinity = 0.7f, bristleSeed = 17, dryPressure = 0.65f,
    )

    private fun renderPaper(stamp: BrushStamp, size: Int, amplitude: Float, callback: (FloatArray?, Map<String, Any>) -> Unit) {
        surface.setDryPaper(amplitude, 1f, PAPER_SEED)
        render(listOf(stamp), size, size, callback)
    }

    private fun render(stamps: List<BrushStamp>, width: Int, height: Int, callback: (FloatArray?, Map<String, Any>) -> Unit) {
        surface.clearCanvas()
        surface.showColoredBrushStamps(stamps.map { ColoredBrushStamp(it, 1f, 1f, 1f, 1f, 1f) }, width, height)
        surface.captureStrokeRgbaForTest(callback)
    }

    private fun estimateGradientOrientation(values: FloatArray, width: Int, cx: Float, cy: Float, radius: Float): Double {
        var jxx = 0.0; var jyy = 0.0; var jxy = 0.0
        for (y in 1 until width - 1) for (x in 1 until width - 1) {
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            if (dx * dx + dy * dy > radius * radius) continue
            val gx = alpha(values, width, x + 1, y) - alpha(values, width, x - 1, y)
            val gy = alpha(values, width, x, y + 1) - alpha(values, width, x, y - 1)
            jxx += gx * gx; jyy += gy * gy; jxy += gx * gy
        }
        return 0.5 * atan2(2.0 * jxy, jxx - jyy)
    }

    private fun orientationErrorDegrees(measured: Double, expected: Double): Double {
        var difference = abs(measured - expected) % PI
        if (difference > PI / 2.0) difference = PI - difference
        return Math.toDegrees(difference)
    }

    private fun alpha(values: FloatArray, width: Int, x: Int, y: Int) = values[(y * width + x) * 4 + 3].toDouble()

    private fun correlation(x: List<Double>, y: List<Double>): Double {
        val mx = x.average(); val my = y.average()
        var numerator = 0.0; var xx = 0.0; var yy = 0.0
        for (i in x.indices) { val dx = x[i] - mx; val dy = y[i] - my; numerator += dx * dy; xx += dx * dx; yy += dy * dy }
        return if (xx == 0.0 || yy == 0.0) 0.0 else numerator / kotlin.math.sqrt(xx * yy)
    }

    private fun variance(values: List<Double>): Double {
        val mean = values.average()
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }

    private fun coverageRegion(values: FloatArray, width: Int, height: Int): List<Double> {
        val output = ArrayList<Double>(500 * 20)
        val y0 = height / 2 - 10
        val y1 = height / 2 + 10
        for (y in y0 until y1) for (x in 6..506) output += alpha(values, width, x, y)
        return output
    }

    private fun standardDeviation(values: List<Double>): Double = sqrt(variance(values))

    private fun ssim(a: List<Double>, b: List<Double>): Double {
        require(a.size == b.size && a.isNotEmpty())
        val meanA = a.average(); val meanB = b.average()
        var varianceA = 0.0; var varianceB = 0.0; var covariance = 0.0
        for (i in a.indices) {
            val da = a[i] - meanA; val db = b[i] - meanB
            varianceA += da * da; varianceB += db * db; covariance += da * db
        }
        val denominator = maxOf(1, a.size - 1).toDouble()
        varianceA /= denominator; varianceB /= denominator; covariance /= denominator
        val c1 = .01 * .01; val c2 = .03 * .03
        return ((2.0 * meanA * meanB + c1) * (2.0 * covariance + c2)) /
            ((meanA * meanA + meanB * meanB + c1) * (varianceA + varianceB + c2))
    }

    private fun linearR2(x: List<Double>, y: List<Double>): Double {
        val mx = x.average(); val my = y.average()
        val slope = x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / x.sumOf { (it - mx) * (it - mx) }
        val intercept = my - slope * mx
        val total = y.sumOf { (it - my) * (it - my) }
        val residual = x.indices.sumOf { val e = y[it] - (intercept + slope * x[it]); e * e }
        return if (total == 0.0) 1.0 else 1.0 - residual / total
    }

    private fun evidenceRoot(gate: String): File {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return File(getExternalFilesDir(null), "evidence/$date/G5/$gate/uncommitted").also { it.mkdirs() }
    }

    private fun finishResult(gate: String, pass: Boolean, metrics: JSONObject) {
        val root = evidenceRoot(gate)
        File(root, "metrics.json").writeText(metrics.toString(2))
        File(root, "summary.json").writeText(JSONObject().put("gate", "G5-$gate")
            .put("result", if (pass) "PASS" else "FAIL").put("metrics", metrics).toString(2))
        File(root, "log.txt").writeText("G5-$gate=${if (pass) "PASS" else "FAIL"}\n${metrics.toString(2)}\n")
        Log.d("G5-$gate", "result=${if (pass) "PASS" else "FAIL"} metrics=$metrics")
        surface.postDelayed({ finish() }, 300)
    }

    private fun saveFloatPng(values: FloatArray, width: Int, height: Int, file: File) {
        val colors = IntArray(width * height)
        for (pixel in colors.indices) {
            val base = pixel * 4
            colors[pixel] = Color.argb(byte(values[base + 3]), byte(values[base]), byte(values[base + 1]), byte(values[base + 2]))
        }
        val bitmap = android.graphics.Bitmap.createBitmap(colors, width, height, android.graphics.Bitmap.Config.ARGB_8888)
        file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun byte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    companion object {
        private const val DEPLETION = 0.0011f
        private const val PAPER_SEED = 23
    }
}
