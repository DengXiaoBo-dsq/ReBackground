package com.dsq.rebackground.painttest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.dsq.rebackground.paint.brush.BrushStamp
import com.dsq.rebackground.paint.brush.BrushTextureSamplingMode
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.rendering.gl.ColoredBrushStamp
import com.dsq.rebackground.paint.rendering.gl.PaintGLSurfaceView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

class G2FidelityActivity : AppCompatActivity() {
    private lateinit var surface: PaintGLSurfaceView
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = PaintGLSurfaceView(this)
        setContentView(surface)
        surface.setDebugGlReadyListener {
            if (!started) {
                started = true
                val gate = intent.getStringExtra("gate") ?: "B"
                runOnUiThread {
                    when (gate.uppercase(Locale.US)) {
                        "B" -> runRotation()
                        "C" -> runAlphaEdge()
                        "D" -> runAspect()
                        else -> finishWithLog(gate, false, "unknown gate")
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); surface.onResume() }
    override fun onPause() { surface.onPause(); super.onPause() }

    private fun runRotation() {
        val canvasSize = 96
        val source = pngRoundTrip(createRotationFixture())
        val angles = listOf(15, 30, 45, 60, 90, 135, 180, 270)
        val rows = JSONArray()
        val root = evidenceRoot("B")
        savePng(source, File(root, "input.png"))
        surface.setDocumentSize(canvasSize, canvasSize)

        fun next(index: Int) {
            if (index == angles.size) {
                val pass = (0 until rows.length()).all { rows.getJSONObject(it).getBoolean("pass") }
                writeSummary(root, "G2-B", pass, rows)
                finishWithLog("B", pass, rows.toString())
                return
            }
            val degrees = angles[index]
            val radians = Math.toRadians(degrees.toDouble()).toFloat()
            render(
                source,
                canvasSize,
                BrushStamp(
                    center = Vec2(canvasSize / 2f, canvasSize / 2f),
                    diameterDocumentUnits = source.height.toFloat(),
                    aspectRatio = 1f,
                    rotationRadians = radians,
                    textureResourceKey = TEXTURE_KEY,
                    textureSamplingMode = BrushTextureSamplingMode.SOURCE_RGBA,
                ),
            ) { actual, renderMetrics ->
                val reference = referenceSample(source, canvasSize, canvasSize, source.height.toFloat(), 1f, radians)
                val metrics = compare(reference, actual)
                val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
                val pass = actual != null && metrics.rgbMae <= THREE_LSB && metrics.ssim >= 0.999 && glError == 0
                rows.put(JSONObject().apply {
                    put("degrees", degrees)
                    put("rgbMae", metrics.rgbMae)
                    put("alphaMae", metrics.alphaMae)
                    put("maxError", metrics.maxError)
                    put("ssim", metrics.ssim)
                    put("glError", glError)
                    put("pass", pass)
                })
                saveFloatPng(reference, canvasSize, canvasSize, File(root, "reference-$degrees.png"))
                actual?.let {
                    saveFloatPng(it, canvasSize, canvasSize, File(root, "actual-$degrees.png"))
                    saveDiffPng(reference, it, canvasSize, canvasSize, File(root, "diff-$degrees.png"))
                }
                next(index + 1)
            }
        }
        next(0)
    }

    private fun runAlphaEdge() {
        val canvasSize = 128
        val variants = listOf("transparent-black", "transparent-white", "semi-transparent-edge")
        val rows = JSONArray()
        val root = evidenceRoot("C")
        surface.setDocumentSize(canvasSize, canvasSize)

        fun next(index: Int) {
            if (index == variants.size) {
                val pass = (0 until rows.length()).all { rows.getJSONObject(it).getBoolean("pass") }
                writeSummary(root, "G2-C", pass, rows)
                finishWithLog("C", pass, rows.toString())
                return
            }
            val variant = variants[index]
            val source = pngRoundTrip(createAlphaFixture(variant))
            savePng(source, File(root, "input-$variant.png"))
            val radians = Math.toRadians(45.0).toFloat()
            render(
                source,
                canvasSize,
                BrushStamp(
                    center = Vec2(canvasSize / 2f, canvasSize / 2f),
                    diameterDocumentUnits = 64f,
                    aspectRatio = 1f,
                    rotationRadians = radians,
                    textureResourceKey = TEXTURE_KEY,
                    textureSamplingMode = BrushTextureSamplingMode.SOURCE_RGBA,
                ),
            ) { actual, renderMetrics ->
                val reference = referenceSample(source, canvasSize, canvasSize, 64f, 1f, radians)
                val metrics = compare(reference, actual)
                val halo = haloMetrics(actual, canvasSize, TARGET_RGB)
                val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
                val pass = actual != null && metrics.alphaMae <= ONE_LSB &&
                    halo.maxRgbError <= TWO_LSB && halo.darkClusters == 0 && halo.brightClusters == 0 && glError == 0
                rows.put(JSONObject().apply {
                    put("fixture", variant)
                    put("alphaMae", metrics.alphaMae)
                    put("rgbMae", metrics.rgbMae)
                    put("haloRgbError", halo.maxRgbError)
                    put("edgePixels", halo.edgePixels)
                    put("unexpectedDarkCluster", halo.darkClusters)
                    put("unexpectedBrightCluster", halo.brightClusters)
                    put("glError", glError)
                    put("pass", pass)
                })
                saveFloatPng(reference, canvasSize, canvasSize, File(root, "reference-$variant.png"))
                actual?.let {
                    saveFloatPng(it, canvasSize, canvasSize, File(root, "actual-$variant.png"))
                    saveDiffPng(reference, it, canvasSize, canvasSize, File(root, "diff-$variant.png"))
                }
                next(index + 1)
            }
        }
        next(0)
    }

    private fun runAspect() {
        val canvasSize = 1024
        val dimensions = listOf(1024 to 256, 256 to 1024, 1000 to 333, 333 to 1000)
        val rows = JSONArray()
        val root = evidenceRoot("D")
        surface.setDocumentSize(canvasSize, canvasSize)

        fun next(index: Int) {
            if (index == dimensions.size) {
                val pass = (0 until rows.length()).all { rows.getJSONObject(it).getBoolean("pass") }
                writeSummary(root, "G2-D", pass, rows)
                finishWithLog("D", pass, rows.toString())
                return
            }
            val (width, height) = dimensions[index]
            val source = pngRoundTrip(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            })
            render(
                source,
                canvasSize,
                BrushStamp(
                    center = Vec2(canvasSize / 2f, canvasSize / 2f),
                    diameterDocumentUnits = 240f,
                    // Deliberately neutral: SOURCE_RGBA must derive the source aspect itself.
                    aspectRatio = 1f,
                    rotationRadians = 0f,
                    textureResourceKey = TEXTURE_KEY,
                    textureSamplingMode = BrushTextureSamplingMode.SOURCE_RGBA,
                ),
            ) { actual, renderMetrics ->
                val bounds = actual?.let { alphaBounds(it, canvasSize, canvasSize) }
                val sourceAspect = width.toDouble() / height.toDouble()
                val measuredAspect = bounds?.let { it.width.toDouble() / it.height.toDouble() } ?: 0.0
                val error = abs(measuredAspect - sourceAspect) / sourceAspect
                val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
                val pass = bounds != null && error <= 0.005 && glError == 0
                val name = "${width}x$height"
                rows.put(JSONObject().apply {
                    put("fixture", name)
                    put("sourceAspect", sourceAspect)
                    put("measuredWidth", bounds?.width ?: 0)
                    put("measuredHeight", bounds?.height ?: 0)
                    put("measuredAspect", measuredAspect)
                    put("aspectError", error)
                    put("glError", glError)
                    put("pass", pass)
                })
                actual?.let { saveFloatPng(it, canvasSize, canvasSize, File(root, "actual-$name.png")) }
                next(index + 1)
            }
        }
        next(0)
    }

    private fun render(
        source: Bitmap,
        canvasSize: Int,
        stamp: BrushStamp,
        callback: (FloatArray?, Map<String, Any>) -> Unit,
    ) {
        surface.clearCanvas()
        surface.setBrushTextures(mapOf(TEXTURE_KEY to source))
        surface.showColoredBrushStamps(
            listOf(ColoredBrushStamp(stamp, 1f, 1f, 1f, 1f, 1f)),
            canvasSize,
            canvasSize,
        )
        surface.captureStrokeRgbaForTest(callback)
    }

    private fun referenceSample(
        source: Bitmap,
        canvasWidth: Int,
        canvasHeight: Int,
        stampHeight: Float,
        aspect: Float,
        rotation: Float,
    ): FloatArray {
        val output = FloatArray(canvasWidth * canvasHeight * 4)
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f
        val halfWidth = stampHeight * aspect / 2f
        val halfHeight = stampHeight / 2f
        val c = cos(rotation)
        val s = sin(rotation)
        for (y in 0 until canvasHeight) for (x in 0 until canvasWidth) {
            val dx = x + 0.5f - centerX
            val dy = y + 0.5f - centerY
            val localX = dx * c + dy * s
            val localY = -dx * s + dy * c
            val unitX = localX / halfWidth
            val unitY = localY / halfHeight
            if (abs(unitX) <= 1f && abs(unitY) <= 1f) {
                val sample = alphaSafeBilinear(source, unitX * 0.5f + 0.5f, unitY * 0.5f + 0.5f)
                val base = (y * canvasWidth + x) * 4
                for (component in 0..3) output[base + component] = sample[component]
            }
        }
        return output
    }

    private fun alphaSafeBilinear(bitmap: Bitmap, u: Float, v: Float): FloatArray {
        val px = u * bitmap.width - 0.5f
        val py = v * bitmap.height - 0.5f
        val x0 = floor(px).toInt()
        val y0 = floor(py).toInt()
        val fx = px - floor(px)
        val fy = py - floor(py)
        val points = arrayOf(x0 to y0, x0 + 1 to y0, x0 to y0 + 1, x0 + 1 to y0 + 1)
        val weights = floatArrayOf((1f - fx) * (1f - fy), fx * (1f - fy), (1f - fx) * fy, fx * fy)
        var alpha = 0f
        val premul = FloatArray(3)
        points.forEachIndexed { index, point ->
            val color = bitmap.getPixel(point.first.coerceIn(0, bitmap.width - 1), point.second.coerceIn(0, bitmap.height - 1))
            val a = Color.alpha(color) / 255f
            val weight = weights[index]
            alpha += a * weight
            premul[0] += Color.red(color) / 255f * a * weight
            premul[1] += Color.green(color) / 255f * a * weight
            premul[2] += Color.blue(color) / 255f * a * weight
        }
        return if (alpha <= 0.000001f) FloatArray(4) else floatArrayOf(
            premul[0] / alpha,
            premul[1] / alpha,
            premul[2] / alpha,
            alpha,
        )
    }

    private fun compare(reference: FloatArray, actual: FloatArray?): Metrics {
        if (actual == null || actual.size != reference.size) return Metrics(1.0, 1.0, 1.0, 0.0)
        var rgbSum = 0.0
        var alphaSum = 0.0
        var maxError = 0.0
        val count = reference.size / 4
        val referenceLuma = DoubleArray(count)
        val actualLuma = DoubleArray(count)
        for (pixel in 0 until count) {
            val base = pixel * 4
            for (channel in 0..2) {
                val error = abs(reference[base + channel] - actual[base + channel]).toDouble()
                rgbSum += error
                maxError = max(maxError, error)
            }
            val alphaError = abs(reference[base + 3] - actual[base + 3]).toDouble()
            alphaSum += alphaError
            maxError = max(maxError, alphaError)
            referenceLuma[pixel] = luma(reference, base)
            actualLuma[pixel] = luma(actual, base)
        }
        return Metrics(rgbSum / (count * 3), alphaSum / count, maxError, ssim(referenceLuma, actualLuma))
    }

    private fun haloMetrics(actual: FloatArray?, width: Int, target: FloatArray): HaloMetrics {
        if (actual == null) return HaloMetrics(1.0, 0, 1, 1)
        var maxError = 0.0
        var edgePixels = 0
        var dark = 0
        var bright = 0
        for (pixel in 0 until width * width) {
            val base = pixel * 4
            val alpha = actual[base + 3]
            if (alpha > 0.000001f && alpha <= 0.25f + ONE_LSB.toFloat()) {
                edgePixels++
                val errors = (0..2).map { abs(actual[base + it] - target[it]).toDouble() }
                maxError = max(maxError, errors.maxOrNull() ?: 0.0)
                val actualLuma = luma(actual, base)
                val targetLuma = 0.2126 * target[0] + 0.7152 * target[1] + 0.0722 * target[2]
                if (targetLuma - actualLuma > TWO_LSB) dark++
                if (actualLuma - targetLuma > TWO_LSB) bright++
            }
        }
        return HaloMetrics(maxError, edgePixels, dark, bright)
    }

    private fun alphaBounds(actual: FloatArray, width: Int, height: Int): Bounds? {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (actual[(y * width + x) * 4 + 3] > 0.5f) {
                minX = minOf(minX, x); minY = minOf(minY, y)
                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
        }
        return if (maxX < minX || maxY < minY) null else Bounds(maxX - minX + 1, maxY - minY + 1)
    }

    private fun createRotationFixture(): Bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
        setPremultiplied(false)
        eraseColor(Color.TRANSPARENT)
        for (y in 2 until 30) for (x in 2 until 30) {
            val red = (x * 9 + y * 3) and 0xff
            val green = (x * 2 + y * 11 + 17) and 0xff
            val blue = (x * 13 + y * 5 + 41) and 0xff
            setPixel(x, y, Color.argb(255, red, green, blue))
        }
        for (y in 5..11) for (x in 4..9) setPixel(x, y, Color.WHITE)
        for (y in 20..27) for (x in 22..28) setPixel(x, y, Color.RED)
    }

    private fun createAlphaFixture(variant: String): Bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
        setPremultiplied(false)
        val transparentRgb = if (variant == "transparent-white") 255 else 0
        for (y in 0 until height) for (x in 0 until width) {
            val distance = minOf(x, y, width - 1 - x, height - 1 - y)
            val alpha = when {
                distance <= 3 -> 0
                distance == 4 -> 64
                distance == 5 -> 128
                distance == 6 -> 191
                else -> 255
            }
            val color = if (alpha == 0) {
                Color.argb(0, transparentRgb, transparentRgb, transparentRgb)
            } else {
                Color.argb(alpha, (TARGET_RGB[0] * 255).roundToInt(), (TARGET_RGB[1] * 255).roundToInt(), (TARGET_RGB[2] * 255).roundToInt())
            }
            setPixel(x, y, color)
        }
    }

    private fun pngRoundTrip(source: Bitmap): Bitmap {
        val bytes = ByteArrayOutputStream().use { output ->
            check(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
        }
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
    }

    private fun ssim(a: DoubleArray, b: DoubleArray): Double {
        val meanA = a.average()
        val meanB = b.average()
        var varianceA = 0.0
        var varianceB = 0.0
        var covariance = 0.0
        for (index in a.indices) {
            val da = a[index] - meanA
            val db = b[index] - meanB
            varianceA += da * da
            varianceB += db * db
            covariance += da * db
        }
        varianceA /= a.size
        varianceB /= a.size
        covariance /= a.size
        val c1 = 0.01.pow(2)
        val c2 = 0.03.pow(2)
        return ((2 * meanA * meanB + c1) * (2 * covariance + c2)) /
            ((meanA * meanA + meanB * meanB + c1) * (varianceA + varianceB + c2))
    }

    private fun luma(values: FloatArray, base: Int): Double =
        0.2126 * values[base] + 0.7152 * values[base + 1] + 0.0722 * values[base + 2]

    private fun evidenceRoot(gate: String): File {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return File(getExternalFilesDir(null), "evidence/$date/G2/$gate/uncommitted").also { it.mkdirs() }
    }

    private fun writeSummary(root: File, gate: String, pass: Boolean, rows: JSONArray) {
        val metrics = JSONObject().apply { put("cases", rows) }
        File(root, "metrics.json").writeText(metrics.toString(2))
        File(root, "summary.json").writeText(JSONObject().apply {
            put("gate", gate)
            put("result", if (pass) "PASS" else "FAIL")
            put("metrics", metrics)
        }.toString(2))
        File(root, "log.txt").writeText("$gate=${if (pass) "PASS" else "FAIL"}\n${rows.toString(2)}\n")
    }

    private fun finishWithLog(gate: String, pass: Boolean, details: String) {
        Log.d("G2-$gate", "result=${if (pass) "PASS" else "FAIL"} details=$details")
        surface.postDelayed({ finish() }, 300)
    }

    private fun savePng(bitmap: Bitmap, file: File) {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun saveFloatPng(values: FloatArray, width: Int, height: Int, file: File) {
        val colors = IntArray(width * height)
        for (pixel in colors.indices) {
            val base = pixel * 4
            colors[pixel] = Color.argb(byte(values[base + 3]), byte(values[base]), byte(values[base + 1]), byte(values[base + 2]))
        }
        savePng(Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888), file)
    }

    private fun saveDiffPng(reference: FloatArray, actual: FloatArray, width: Int, height: Int, file: File) {
        val colors = IntArray(width * height)
        for (pixel in colors.indices) {
            val base = pixel * 4
            colors[pixel] = Color.rgb(
                byte(abs(reference[base] - actual[base]) * 16f),
                byte(abs(reference[base + 1] - actual[base + 1]) * 16f),
                byte(abs(reference[base + 2] - actual[base + 2]) * 16f),
            )
        }
        savePng(Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888), file)
    }

    private fun byte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    private data class Metrics(val rgbMae: Double, val alphaMae: Double, val maxError: Double, val ssim: Double)
    private data class HaloMetrics(val maxRgbError: Double, val edgePixels: Int, val darkClusters: Int, val brightClusters: Int)
    private data class Bounds(val width: Int, val height: Int)

    companion object {
        private const val TEXTURE_KEY = "g2-fidelity"
        private const val ONE_LSB = 1.0 / 255.0
        private const val TWO_LSB = 2.0 / 255.0
        private const val THREE_LSB = 3.0 / 255.0
        private val TARGET_RGB = floatArrayOf(0.2f, 0.6f, 0.9f)
    }
}
