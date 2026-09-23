package com.dsq.rebackground.painttest

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.dsq.rebackground.paint.brush.BrushStamp
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.paper.PaperDefinition
import com.dsq.rebackground.paint.paper.PaperPresets
import com.dsq.rebackground.paint.rendering.gl.ColoredBrushStamp
import com.dsq.rebackground.paint.rendering.gl.PaintGLSurfaceView
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * Debug-only evidence generator for the paper selection diagnosis. It deliberately
 * reads the real stroke FBO so every image is document-resolution and independent
 * of screen scaling. It does not alter production paper presets or render passes.
 */
class PaperDiagnosticActivity : AppCompatActivity() {
    private lateinit var surface: PaintGLSurfaceView
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = PaintGLSurfaceView(this)
        setContentView(surface)
        surface.setDebugGlReadyListener {
            if (!started) {
                started = true
                runOnUiThread(::runDiagnostic)
            }
        }
    }

    override fun onResume() { super.onResume(); surface.onResume() }
    override fun onPause() { surface.onPause(); super.onPause() }

    private fun runDiagnostic() {
        surface.setDocumentSize(SIZE, SIZE)
        val smooth = PaperPresets.requireOrDefault(PaperPresets.SMOOTH_ID)
        val rough = PaperPresets.requireOrDefault(PaperPresets.ROUGH_WATERCOLOR_ID)
        // Diagnostic-only maximums. No repository value is mutated or persisted.
        val roughExtreme = rough.copy(
            id = "rough-watercolor-diagnostic-extreme",
            material = rough.material.copy(
                heightAmplitude = 1f,
                roughness = 1f,
                absorption = 1f,
                fiberDensity = 1f,
                grainScale = .25f,
            ),
            seed = 907,
        )
        val captures = LinkedHashMap<String, Bitmap>()
        val root = File(getExternalFilesDir(null), "evidence/paper-diag").also { it.mkdirs() }
        val fixture = pencilFixture()
        val jobs = listOf(
            Job("A1_smooth_nodraw.png", smooth, emptyList()),
            Job("A2_rough_nodraw.png", rough, emptyList()),
            Job("B1_smooth_stroke.png", smooth, fixture),
            Job("B2_rough_stroke.png", rough, fixture),
            Job("C1_smooth_extreme.png", smooth, fixture),
            Job("C2_rough_extreme.png", roughExtreme, fixture),
        ) + responseFixtures().flatMap { fixtureCase ->
            listOf(
                Job("R7_${fixtureCase.name}_smooth.png", smooth, fixtureCase.stamps),
                Job("R7_${fixtureCase.name}_rough.png", rough, fixtureCase.stamps),
            )
        }

        fun next(index: Int) {
            if (index == jobs.size) {
                val result = JSONObject().apply {
                    put("A_smooth_vs_rough", compare(captures.getValue("A1"), captures.getValue("A2")))
                    put("B_stroke_smooth_vs_rough", compare(captures.getValue("B1"), captures.getValue("B2")))
                    put("C_extreme_smooth_vs_rough", compare(captures.getValue("C1"), captures.getValue("C2")))
                    put("R7_allBrushes", JSONObject().apply {
                        responseFixtures().forEach { fixtureCase ->
                            put(fixtureCase.name, compare(
                                captures.getValue("R7_${fixtureCase.name}_smooth"),
                                captures.getValue("R7_${fixtureCase.name}_rough"),
                            ))
                        }
                    })
                    put("capture", "final composite FBO, document-resolution PNG")
                    put("fixture", "pencil, 200px horizontal line, fixed pressure and spacing")
                }
                File(root, "result.json").writeText(result.toString(2))
                Log.d("PaperDiagnostic", result.toString())
                surface.postDelayed({ finish() }, 500)
                return
            }
            val job = jobs[index]
            surface.setPaper(job.paper)
            render(job.stamps) { bitmap ->
                if (bitmap == null) {
                    File(root, "result.json").writeText(JSONObject().put("error", "capture failed: ${job.fileName}").toString(2))
                    finish()
                    return@render
                }
                val key = if (job.fileName.startsWith("R7_")) {
                    job.fileName.removeSuffix(".png")
                } else {
                    job.fileName.substringBefore('_')
                }
                captures[key] = bitmap
                saveBitmap(bitmap, File(root, job.fileName))
                next(index + 1)
            }
        }
        next(0)
    }

    private fun pencilFixture(): List<BrushStamp> = (28..228 step 4).map { x ->
        BrushStamp(
            center = Vec2(x.toFloat(), SIZE / 2f), diameterDocumentUnits = 26f,
            aspectRatio = 1f, rotationRadians = 0f, dryLoad = .85f,
            dryArcLengthDocumentUnits = (x - 28).toFloat(), bristleDensity = 0f,
            paperGrainAffinity = 1f, bristleSeed = 41, dryPressure = .48f,
        )
    }

    private fun responseFixtures(): List<ResponseFixture> = listOf(
        ResponseFixture("electric", .1f),
        ResponseFixture("pencil", 1f),
        ResponseFixture("marker", .4f),
        ResponseFixture("airbrush", .3f),
        ResponseFixture("g61", .6f),
    ).map { fixture ->
        fixture.copy(stamps = (28..228 step 4).map { x ->
            BrushStamp(
                center = Vec2(x.toFloat(), SIZE / 2f), diameterDocumentUnits = 26f,
                aspectRatio = 1f, rotationRadians = 0f, dryLoad = .85f,
                dryArcLengthDocumentUnits = (x - 28).toFloat(), bristleDensity = 0f,
                paperGrainAffinity = .7f, paperResponseStrength = fixture.response,
                bristleSeed = 41, dryPressure = .48f,
            )
        })
    }

    private fun render(stamps: List<BrushStamp>, callback: (Bitmap?) -> Unit) {
        surface.clearCanvas()
        surface.showColoredBrushStamps(
            stamps.map { ColoredBrushStamp(it, 0f, 0f, 0f, 1f, 1f) }, SIZE, SIZE,
        )
        surface.captureBitmapWithMetrics { bitmap, _ -> callback(bitmap) }
    }

    private fun compare(
        a: Bitmap,
        b: Bitmap,
        xStart: Int = 0,
        xEnd: Int = SIZE,
        yStart: Int = 0,
        yEnd: Int = SIZE,
    ): JSONObject {
        require(a.width == b.width && a.height == b.height)
        var mae = 0.0
        var maxError = 0.0
        val luminanceA = DoubleArray((xEnd - xStart) * (yEnd - yStart))
        val luminanceB = DoubleArray(luminanceA.size)
        val pixelsA = IntArray(SIZE * SIZE)
        val pixelsB = IntArray(SIZE * SIZE)
        a.getPixels(pixelsA, 0, SIZE, 0, 0, SIZE, SIZE)
        b.getPixels(pixelsB, 0, SIZE, 0, 0, SIZE, SIZE)
        var sample = 0
        for (y in yStart until yEnd) for (x in xStart until xEnd) {
            val source = y * SIZE + x
            val aa = luminance(pixelsA[source])
            val bb = luminance(pixelsB[source])
            luminanceA[sample] = aa; luminanceB[sample] = bb
            val error = kotlin.math.abs(aa - bb)
            mae += error; maxError = maxOf(maxError, error)
            sample++
        }
        mae /= luminanceA.size
        val ssim = ssim(luminanceA, luminanceB)
        return JSONObject()
            .put("mae", mae)
            .put("maxError", maxError)
            .put("ssim", ssim)
            .put("visible", ssim < .95 || mae >= 2.0 / 255.0)
    }

    private fun ssim(a: DoubleArray, b: DoubleArray): Double {
        val meanA = a.average(); val meanB = b.average()
        var va = 0.0; var vb = 0.0; var covariance = 0.0
        for (i in a.indices) {
            val da = a[i] - meanA; val db = b[i] - meanB
            va += da * da; vb += db * db; covariance += da * db
        }
        val n = (a.size - 1).toDouble()
        va /= n; vb /= n; covariance /= n
        val c1 = .01 * .01; val c2 = .03 * .03
        return ((2.0 * meanA * meanB + c1) * (2.0 * covariance + c2)) /
            ((meanA * meanA + meanB * meanB + c1) * (va + vb + c2))
    }

    private fun luminance(color: Int): Double =
        (((color shr 16) and 0xFF) * .2126 + ((color shr 8) and 0xFF) * .7152 + (color and 0xFF) * .0722) / 255.0

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private data class Job(val fileName: String, val paper: PaperDefinition, val stamps: List<BrushStamp>)
    private data class ResponseFixture(val name: String, val response: Float, val stamps: List<BrushStamp> = emptyList())

    private companion object { const val SIZE = 256 }
}
