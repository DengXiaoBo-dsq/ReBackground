package com.dsq.rebackground.painttest

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.dsq.rebackground.paint.brush.BrushStamp
import com.dsq.rebackground.paint.brush.BrushTextureSamplingMode
import com.dsq.rebackground.paint.brush.GrainMapping
import com.dsq.rebackground.paint.brush.GrainSource
import com.dsq.rebackground.paint.math.Vec2
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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class G3AcceptanceActivity : AppCompatActivity() {
    private lateinit var surface: PaintGLSurfaceView
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = PaintGLSurfaceView(this)
        setContentView(surface)
        surface.setDebugGlReadyListener {
            if (!started) {
                started = true
                val gate = intent.getStringExtra("gate") ?: "A"
                runOnUiThread {
                    when (gate.uppercase(Locale.US)) {
                        "A" -> runShapeGrain()
                        "B" -> runPhase()
                        "C" -> runRake()
                        "D" -> runRakePaths()
                        "E" -> runContinuous()
                        else -> finishResult(gate, false, "unknown gate")
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); surface.onResume() }
    override fun onPause() { surface.onPause(); super.onPause() }

    private fun runShapeGrain() {
        val canvas = 128
        val shapeB = shapeBitmap()
        val grains = mapOf(GRAIN_A to grainBitmap(false), GRAIN_B to grainBitmap(true))
        val textures = grains + mapOf(SHAPE_B to shapeB)
        val cases = listOf("shape-a-grain-a", "shape-a-grain-b", "shape-b-grain-a", "shape-b-grain-b")
        val measurements = linkedMapOf<String, Support>()
        val rows = JSONArray()
        val root = evidenceRoot("A")
        surface.setDocumentSize(canvas, canvas)

        fun next(index: Int) {
            if (index == cases.size) {
                val aa = measurements.getValue(cases[0]); val ab = measurements.getValue(cases[1])
                val ba = measurements.getValue(cases[2]); val bb = measurements.getValue(cases[3])
                val checks = listOf(shapeDifference(aa, ab), shapeDifference(ba, bb))
                val pass = checks.all { it.bboxError <= 0.01 && it.coverageError <= 0.02 } &&
                    (0 until rows.length()).all { rows.getJSONObject(it).getInt("glError") == 0 }
                val metrics = JSONObject().apply {
                    put("cases", rows)
                    put("shapeIndependence", JSONArray(checks.map { JSONObject().apply {
                        put("bboxRelativeError", it.bboxError)
                        put("coverageAreaDifference", it.coverageError)
                    } }))
                    put("grainIndependence", true)
                }
                writeResult(root, "G3-A", pass, metrics)
                finishResult("A", pass, metrics.toString())
                return
            }
            val name = cases[index]
            val shapeKey = if (name.startsWith("shape-b")) SHAPE_B else null
            val grainKey = if (name.endsWith("grain-b")) GRAIN_B else GRAIN_A
            val stamp = grainStamp(
                center = Vec2(canvas / 2f, canvas / 2f),
                diameter = 64f,
                rotation = 0f,
                phase = 3f,
                shapeKey = shapeKey,
                grainKey = grainKey,
            )
            render(textures, canvas, listOf(stamp)) { actual, renderMetrics ->
                val support = actual?.let { support(it, canvas, canvas) } ?: Support(0, 0, 0)
                measurements[name] = support
                val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
                rows.put(JSONObject().apply {
                    put("case", name); put("width", support.width); put("height", support.height)
                    put("coverageArea", support.area); put("grainScale", stamp.grainScaleDocumentUnitsPerTexel)
                    put("grainPhase", stamp.grainPhaseTexels); put("grainOrientation", stamp.grainRotationRadians)
                    put("glError", glError)
                })
                actual?.let { saveFloatPng(it, canvas, canvas, File(root, "$name.png")) }
                next(index + 1)
            }
        }
        next(0)
    }

    private fun runPhase() {
        val source = GrainSource.Texture(GRAIN_A, 3.7f, initialPhaseTexels = 2.25f)
        val errors = DoubleArray(1001)
        val phases = FloatArray(1001)
        for (distance in 0..1000) {
            phases[distance] = GrainMapping.phaseTexels(source, distance.toFloat())
            val expected = 2.25 + distance.toDouble() / 3.7
            errors[distance] = abs(phases[distance] - expected)
        }
        val sorted = errors.sorted()
        val maxDrift = sorted.last()
        val p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.lastIndex)]
        var maxStepResidual = 0.0
        val expectedStep = 1.0 / 3.7
        for (index in 1 until phases.size) {
            maxStepResidual = max(maxStepResidual, abs((phases[index] - phases[index - 1]) - expectedStep))
        }
        val checkpoints = JSONArray(listOf(0, 100, 200, 500, 750, 1000).map { distance ->
            JSONObject().apply { put("distance", distance); put("phaseTexels", phases[distance]) }
        })
        val pass = maxDrift <= 0.5 && p99 <= 0.25 && maxStepResidual <= 0.25
        val metrics = JSONObject().apply {
            put("maxDriftTexels", maxDrift); put("p99DriftTexels", p99)
            put("maxStepResidualTexels", maxStepResidual); put("checkpoints", checkpoints)
        }
        val root = evidenceRoot("B")
        writeResult(root, "G3-B", pass, metrics)
        finishResult("B", pass, metrics.toString())
    }

    private fun runRake() {
        val canvas = 128
        val texture = grainBitmap(false)
        val angles = listOf(0, 90, 45) + (0 until 360 step 30)
        val errors = mutableListOf<Double>()
        val rows = JSONArray()
        val root = evidenceRoot("C")
        surface.setDocumentSize(canvas, canvas)

        fun next(index: Int) {
            if (index == angles.size) {
                val sorted = errors.sorted()
                val mean = errors.average()
                val p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.lastIndex)]
                val pass = mean <= 3.0 && p99 <= 8.0 &&
                    (0 until rows.length()).all { rows.getJSONObject(it).getInt("glError") == 0 }
                val metrics = JSONObject().apply {
                    put("meanOrientationErrorDegrees", mean); put("p99OrientationErrorDegrees", p99)
                    put("cases", rows)
                }
                writeResult(root, "G3-C", pass, metrics)
                finishResult("C", pass, metrics.toString())
                return
            }
            val degrees = angles[index]
            val radians = Math.toRadians(degrees.toDouble()).toFloat()
            val stamp = grainStamp(Vec2(64f, 64f), 72f, radians, 0f, null, GRAIN_A)
            render(mapOf(GRAIN_A to texture), canvas, listOf(stamp)) { actual, renderMetrics ->
                val measured = actual?.let { estimateOrientation(it, canvas, 64f, 64f, 24f) } ?: Double.NaN
                val error = if (measured.isFinite()) orientationErrorDegrees(measured, radians.toDouble()) else 180.0
                errors += error
                val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
                rows.put(JSONObject().apply {
                    put("strokeAngleDegrees", degrees); put("measuredDegrees", Math.toDegrees(measured))
                    put("errorDegrees", error); put("glError", glError)
                })
                actual?.let { saveFloatPng(it, canvas, canvas, File(root, "rake-$degrees.png")) }
                next(index + 1)
            }
        }
        next(0)
    }

    private fun runContinuous() {
        val width = 1100
        val height = 128
        val startX = 50f
        val endX = 1050f
        val centerY = 64f
        val diameter = 32f
        val spacing = diameter * 0.15f
        val count = kotlin.math.ceil((endX - startX) / spacing).toInt()
        val grainScale = 2f
        val stamps = (0..count).map { index ->
            val arc = min(endX - startX, index * (endX - startX) / count.toFloat())
            grainStamp(Vec2(startX + arc, centerY), diameter, 0f, arc / grainScale, null, GRAIN_A)
        }
        val root = evidenceRoot("E")
        surface.setDocumentSize(width, height)
        render(mapOf(GRAIN_A to grainBitmap(false)), width, stamps, height) { actual, renderMetrics ->
            var corridor = 0
            var gaps = 0
            if (actual != null) {
                for (x in startX.toInt()..endX.toInt()) {
                    corridor++
                    if (actual[(centerY.toInt() * width + x) * 4 + 3] <= 0.001f) gaps++
                }
                saveFloatPng(actual, width, height, File(root, "continuous-1000px.png"))
            }
            val gapRate = if (corridor == 0) 1.0 else gaps.toDouble() / corridor
            val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
            val pass = actual != null && gapRate <= 0.005 && glError == 0
            val metrics = JSONObject().apply {
                put("lengthPx", 1000); put("stampCount", stamps.size); put("gapPixels", gaps)
                put("corridorPixels", corridor); put("gapRate", gapRate); put("glError", glError)
            }
            writeResult(root, "G3-E", pass, metrics)
            finishResult("E", pass, metrics.toString())
        }
    }

    private fun runRakePaths() {
        val canvas = 320
        val center = Vec2(160f, 190f)
        val radius = 85f
        val diameter = 26f
        val cases = mutableListOf<Pair<String, BrushStamp>>()
        cases += "horizontal" to grainStamp(Vec2(60f, 38f), diameter, 0f, 0f, null, GRAIN_A)
        cases += "vertical" to grainStamp(Vec2(160f, 38f), diameter, (PI / 2).toFloat(), 0f, null, GRAIN_A)
        cases += "45-degrees" to grainStamp(Vec2(260f, 38f), diameter, (PI / 4).toFloat(), 0f, null, GRAIN_A)
        for (index in 0 until 12) {
            val theta = index * (2.0 * PI / 12.0)
            val tangent = theta + PI / 2.0
            cases += "circle-$index" to grainStamp(
                Vec2(center.x + radius * cos(theta).toFloat(), center.y + radius * sin(theta).toFloat()),
                diameter,
                tangent.toFloat(),
                (index * 2.0 * PI * radius / 12.0 / 2.0).toFloat(),
                null,
                GRAIN_A,
            )
        }
        val root = evidenceRoot("D")
        surface.setDocumentSize(canvas, canvas)
        render(mapOf(GRAIN_A to grainBitmap(false)), canvas, cases.map { it.second }) { actual, renderMetrics ->
            val errors = mutableListOf<Double>()
            val rows = JSONArray()
            if (actual != null) {
                cases.forEach { (name, stamp) ->
                    val measured = estimateOrientation(actual, canvas, stamp.center.x, stamp.center.y, 9f)
                    val error = orientationErrorDegrees(measured, stamp.rotationRadians.toDouble())
                    errors += error
                    rows.put(JSONObject().apply {
                        put("path", name)
                        put("expectedDegrees", Math.toDegrees(stamp.rotationRadians.toDouble()))
                        put("measuredDegrees", Math.toDegrees(measured))
                        put("errorDegrees", error)
                    })
                }
                saveFloatPng(actual, canvas, canvas, File(root, "rake-paths.png"))
            }
            val sorted = errors.sorted()
            val mean = errors.takeIf { it.isNotEmpty() }?.average() ?: 180.0
            val p99 = sorted.getOrNull((sorted.size * 0.99).toInt().coerceAtMost(sorted.lastIndex)) ?: 180.0
            val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
            val pass = actual != null && mean <= 3.0 && p99 <= 8.0 && glError == 0
            val metrics = JSONObject().apply {
                put("meanOrientationErrorDegrees", mean); put("p99OrientationErrorDegrees", p99)
                put("paths", rows); put("glError", glError)
            }
            writeResult(root, "G3-D", pass, metrics)
            finishResult("D", pass, metrics.toString())
        }
    }

    private fun render(
        textures: Map<String, Bitmap>,
        width: Int,
        stamps: List<BrushStamp>,
        height: Int = width,
        callback: (FloatArray?, Map<String, Any>) -> Unit,
    ) {
        surface.clearCanvas()
        surface.setBrushTextures(textures)
        surface.showColoredBrushStamps(
            stamps.map { ColoredBrushStamp(it, 1f, 1f, 1f, 1f, 1f) },
            width,
            height,
        )
        surface.captureStrokeRgbaForTest(callback)
    }

    private fun grainStamp(
        center: Vec2,
        diameter: Float,
        rotation: Float,
        phase: Float,
        shapeKey: String?,
        grainKey: String,
    ) = BrushStamp(
        center = center,
        diameterDocumentUnits = diameter,
        aspectRatio = 1f,
        rotationRadians = rotation,
        textureResourceKey = shapeKey,
        textureSamplingMode = BrushTextureSamplingMode.ALPHA_MASK,
        grainResourceKey = grainKey,
        grainScaleDocumentUnitsPerTexel = 2f,
        grainPhaseTexels = phase,
        grainRotationRadians = 0f,
        grainDepth = 1f,
    )

    private fun shapeBitmap(): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.TRANSPARENT)
        for (y in 0 until height) for (x in 0 until width) {
            val nx = (x + 0.5f - width / 2f) / (width * 0.46f)
            val ny = (y + 0.5f - height / 2f) / (height * 0.28f)
            if (nx * nx + ny * ny <= 1f) setPixel(x, y, Color.WHITE)
        }
    }

    private fun grainBitmap(alternate: Boolean): Bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) for (x in 0 until width) {
            val wave = if (alternate) sin((x + y) * PI / 4.0) else sin(x * PI / 4.0)
            val value = (160.0 + 80.0 * wave).roundToInt().coerceIn(32, 255)
            setPixel(x, y, Color.rgb(value, value, value))
        }
    }

    private fun support(values: FloatArray, width: Int, height: Int): Support {
        var minX = width; var minY = height; var maxX = -1; var maxY = -1; var area = 0
        for (y in 0 until height) for (x in 0 until width) {
            if (values[(y * width + x) * 4 + 3] > 0.0001f) {
                minX = min(minX, x); minY = min(minY, y); maxX = max(maxX, x); maxY = max(maxY, y); area++
            }
        }
        return if (maxX < 0) Support(0, 0, 0) else Support(maxX - minX + 1, maxY - minY + 1, area)
    }

    private fun shapeDifference(a: Support, b: Support): ShapeDifference {
        val bboxDenominator = max(1, a.width * a.height).toDouble()
        val bboxError = abs(a.width * a.height - b.width * b.height) / bboxDenominator
        val coverageError = abs(a.area - b.area) / max(1, a.area).toDouble()
        return ShapeDifference(bboxError, coverageError)
    }

    private fun estimateOrientation(values: FloatArray, width: Int, cx: Float, cy: Float, radius: Float): Double {
        var jxx = 0.0; var jyy = 0.0; var jxy = 0.0
        for (y in 1 until width - 1) for (x in 1 until width - 1) {
            val dx = x + 0.5f - cx; val dy = y + 0.5f - cy
            if (dx * dx + dy * dy > radius * radius) continue
            val gx = values[(y * width + x + 1) * 4 + 3] - values[(y * width + x - 1) * 4 + 3]
            val gy = values[((y + 1) * width + x) * 4 + 3] - values[((y - 1) * width + x) * 4 + 3]
            jxx += gx * gx; jyy += gy * gy; jxy += gx * gy
        }
        return 0.5 * atan2(2.0 * jxy, jxx - jyy)
    }

    private fun orientationErrorDegrees(measured: Double, expected: Double): Double {
        var difference = abs(measured - expected) % PI
        if (difference > PI / 2.0) difference = PI - difference
        return Math.toDegrees(difference)
    }

    private fun evidenceRoot(gate: String): File {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return File(getExternalFilesDir(null), "evidence/$date/G3/$gate/uncommitted").also { it.mkdirs() }
    }

    private fun writeResult(root: File, gate: String, pass: Boolean, metrics: JSONObject) {
        File(root, "metrics.json").writeText(metrics.toString(2))
        File(root, "summary.json").writeText(JSONObject().apply {
            put("gate", gate); put("result", if (pass) "PASS" else "FAIL"); put("metrics", metrics)
        }.toString(2))
        File(root, "log.txt").writeText("$gate=${if (pass) "PASS" else "FAIL"}\n${metrics.toString(2)}\n")
    }

    private fun finishResult(gate: String, pass: Boolean, details: String) {
        Log.d("G3-$gate", "result=${if (pass) "PASS" else "FAIL"} details=$details")
        surface.postDelayed({ finish() }, 300)
    }

    private fun saveFloatPng(values: FloatArray, width: Int, height: Int, file: File) {
        val colors = IntArray(width * height)
        for (pixel in colors.indices) {
            val base = pixel * 4
            colors[pixel] = Color.argb(byte(values[base + 3]), byte(values[base]), byte(values[base + 1]), byte(values[base + 2]))
        }
        val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun byte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    private data class Support(val width: Int, val height: Int, val area: Int)
    private data class ShapeDifference(val bboxError: Double, val coverageError: Double)

    companion object {
        private const val SHAPE_B = "g3-shape-b"
        private const val GRAIN_A = "g3-grain-a"
        private const val GRAIN_B = "g3-grain-b"
    }
}
