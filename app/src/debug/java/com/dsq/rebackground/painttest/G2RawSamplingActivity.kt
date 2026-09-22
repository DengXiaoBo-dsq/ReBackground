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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class G2RawSamplingActivity : AppCompatActivity() {
    private lateinit var surface: PaintGLSurfaceView
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = PaintGLSurfaceView(this)
        setContentView(surface)
        surface.setDebugGlReadyListener {
            if (!started) {
                started = true
                runOnUiThread { runFixture() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        surface.onResume()
    }

    override fun onPause() {
        surface.onPause()
        super.onPause()
    }

    private fun runFixture() {
        val source = createPngRoundTripFixture()
        surface.setDocumentSize(CANVAS_SIZE, CANVAS_SIZE)
        surface.clearCanvas()
        surface.setBrushTextures(mapOf(TEXTURE_KEY to source))
        surface.showColoredBrushStamps(
            listOf(
                ColoredBrushStamp(
                    stamp = BrushStamp(
                        center = Vec2(CANVAS_SIZE / 2f, CANVAS_SIZE / 2f),
                        diameterDocumentUnits = FIXTURE_SIZE.toFloat(),
                        aspectRatio = 1f,
                        rotationRadians = 0f,
                        textureResourceKey = TEXTURE_KEY,
                        textureSamplingMode = BrushTextureSamplingMode.SOURCE_RGBA,
                    ),
                    red = 1f,
                    green = 1f,
                    blue = 1f,
                    alpha = 1f,
                    flow = 1f,
                ),
            ),
            CANVAS_SIZE,
            CANVAS_SIZE,
        )
        surface.captureStrokeRgbaForTest { pixels, renderMetrics ->
            val result = pixels?.let { compare(source, it) }
            val glError = (renderMetrics["glError"] as? Number)?.toInt() ?: -1
            val pass = result != null &&
                result.rgbMae <= ONE_LSB &&
                result.alphaMae <= ONE_LSB &&
                result.maxError <= TWO_LSB &&
                glError == 0
            val evidence = writeEvidence(source, pixels, result, renderMetrics, pass)
            Log.d(
                TAG,
                "rgbMae=${result?.rgbMae} alphaMae=${result?.alphaMae} " +
                    "maxError=${result?.maxError} glError=$glError result=${if (pass) "PASS" else "FAIL"} " +
                    "evidence=${evidence.absolutePath}",
            )
            surface.postDelayed({ finish() }, 300)
        }
    }

    private fun createPngRoundTripFixture(): Bitmap {
        val original = Bitmap.createBitmap(FIXTURE_SIZE, FIXTURE_SIZE, Bitmap.Config.ARGB_8888)
        original.setPremultiplied(false)
        for (y in 0 until FIXTURE_SIZE) {
            for (x in 0 until FIXTURE_SIZE) {
                val alpha = intArrayOf(64, 128, 192, 255)[(x + y) % 4]
                val red = (x * 17 + y * 11) and 0xff
                val green = (x * 7 + y * 19 + 31) and 0xff
                val blue = (x * 23 + y * 5 + 67) and 0xff
                original.setPixel(x, y, Color.argb(alpha, red, green, blue))
            }
        }
        val encoded = ByteArrayOutputStream().use { stream ->
            check(original.compress(Bitmap.CompressFormat.PNG, 100, stream))
            stream.toByteArray()
        }
        original.recycle()
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
        }
        return requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options))
    }

    private fun compare(source: Bitmap, actual: FloatArray): Result {
        var rgbSum = 0.0
        var alphaSum = 0.0
        var maxError = 0.0
        for (y in 0 until FIXTURE_SIZE) {
            for (x in 0 until FIXTURE_SIZE) {
                val expected = source.getPixel(x, y)
                val base = ((y + ROI_OFFSET) * CANVAS_SIZE + x + ROI_OFFSET) * 4
                val errors = doubleArrayOf(
                    abs(actual[base].toDouble() - Color.red(expected) / 255.0),
                    abs(actual[base + 1].toDouble() - Color.green(expected) / 255.0),
                    abs(actual[base + 2].toDouble() - Color.blue(expected) / 255.0),
                    abs(actual[base + 3].toDouble() - Color.alpha(expected) / 255.0),
                )
                rgbSum += errors[0] + errors[1] + errors[2]
                alphaSum += errors[3]
                errors.forEach { maxError = max(maxError, it) }
            }
        }
        val count = FIXTURE_SIZE * FIXTURE_SIZE
        return Result(rgbSum / (count * 3), alphaSum / count, maxError)
    }

    private fun writeEvidence(
        source: Bitmap,
        pixels: FloatArray?,
        result: Result?,
        renderMetrics: Map<String, Any>,
        pass: Boolean,
    ): File {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val root = File(getExternalFilesDir(null), "evidence/$date/G2/A/uncommitted").also { it.mkdirs() }
        savePng(source, File(root, "input.png"))
        savePng(source, File(root, "reference.png"))
        if (pixels != null) {
            val actual = roiBitmap(pixels)
            savePng(actual, File(root, "actual.png"))
            savePng(diffBitmap(source, actual), File(root, "diff.png"))
        }
        val metrics = JSONObject().apply {
            put("rgbMae", result?.rgbMae ?: JSONObject.NULL)
            put("alphaMae", result?.alphaMae ?: JSONObject.NULL)
            put("maxError", result?.maxError ?: JSONObject.NULL)
            put("rgbMaeThreshold", ONE_LSB)
            put("alphaMaeThreshold", ONE_LSB)
            put("maxErrorThreshold", TWO_LSB)
            put("glError", renderMetrics["glError"] ?: JSONObject.NULL)
        }
        File(root, "metrics.json").writeText(metrics.toString(2))
        File(root, "summary.json").writeText(
            JSONObject().apply {
                put("gate", "G2-A")
                put("spec", "Raw Sampling PNG WYSIWYG")
                put("result", if (pass) "PASS" else "FAIL")
                put("condition", "rotation=0, scale=1, opacity=1, flow=1")
                put("fixture", "16x16 decoded PNG into a texel-aligned 16x16 stamp")
                put("metrics", metrics)
            }.toString(2),
        )
        File(root, "log.txt").writeText(
            "G2-A=${if (pass) "PASS" else "FAIL"}\n" +
                "rgbMae=${result?.rgbMae}\nalphaMae=${result?.alphaMae}\nmaxError=${result?.maxError}\n" +
                "renderMetrics=$renderMetrics\n",
        )
        return root
    }

    private fun roiBitmap(pixels: FloatArray): Bitmap {
        val colors = IntArray(FIXTURE_SIZE * FIXTURE_SIZE)
        for (y in 0 until FIXTURE_SIZE) for (x in 0 until FIXTURE_SIZE) {
            val base = ((y + ROI_OFFSET) * CANVAS_SIZE + x + ROI_OFFSET) * 4
            colors[y * FIXTURE_SIZE + x] = Color.argb(
                byte(pixels[base + 3]),
                byte(pixels[base]),
                byte(pixels[base + 1]),
                byte(pixels[base + 2]),
            )
        }
        return Bitmap.createBitmap(colors, FIXTURE_SIZE, FIXTURE_SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun diffBitmap(reference: Bitmap, actual: Bitmap): Bitmap {
        val colors = IntArray(FIXTURE_SIZE * FIXTURE_SIZE)
        for (y in 0 until FIXTURE_SIZE) for (x in 0 until FIXTURE_SIZE) {
            val a = reference.getPixel(x, y)
            val b = actual.getPixel(x, y)
            colors[y * FIXTURE_SIZE + x] = Color.rgb(
                ((abs(Color.red(a) - Color.red(b)) * 16).coerceAtMost(255)),
                ((abs(Color.green(a) - Color.green(b)) * 16).coerceAtMost(255)),
                ((abs(Color.blue(a) - Color.blue(b)) * 16).coerceAtMost(255)),
            )
        }
        return Bitmap.createBitmap(colors, FIXTURE_SIZE, FIXTURE_SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun byte(value: Float): Int = (value.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun savePng(bitmap: Bitmap, file: File) {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private data class Result(val rgbMae: Double, val alphaMae: Double, val maxError: Double)

    companion object {
        private const val TAG = "G2-A"
        private const val CANVAS_SIZE = 32
        private const val FIXTURE_SIZE = 16
        private const val ROI_OFFSET = (CANVAS_SIZE - FIXTURE_SIZE) / 2
        private const val TEXTURE_KEY = "g2-a-identity"
        private const val ONE_LSB = 1.0 / 255.0
        private const val TWO_LSB = 2.0 / 255.0
    }
}
