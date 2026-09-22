package com.dsq.rebackground.painttest

import android.graphics.Bitmap
import android.opengl.GLES30
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.dsq.rebackground.paint.rendering.gl.PaintGLSurfaceView
import com.dsq.rebackground.paint.ui.PaintEngineController
import com.dsq.rebackground.painttest.gpu.FboProbe
import com.dsq.rebackground.painttest.gpu.GpuCapabilityProbe
import com.dsq.rebackground.painttest.report.EvidenceWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaintTestActivity : AppCompatActivity() {

    private lateinit var surface: PaintGLSurfaceView
    private lateinit var controller: PaintEngineController
    private var glVersion: String = ""
    private var glslVersion: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = PaintGLSurfaceView(this)
        setContentView(surface)
        controller = PaintEngineController(surface)

        val fixtureReq = intent.getStringExtra("fixture")

        surface.setDebugGlReadyListener {
            glVersion = GLES30.glGetString(GLES30.GL_VERSION) ?: "?"
            glslVersion = GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION) ?: "?"

            if (fixtureReq == null) {
                runBatch1Probes()
            } else {
                surface.setDocumentSize(1024, 1024)
                controller.attach(surface.width, surface.height, 1024, 1024)
                if (fixtureReq == "ALL") runAllFixtures()
                else runOneFixture(fixtureReq)
            }
        }
    }

    override fun onResume() { super.onResume(); surface.onResume() }
    override fun onPause() { surface.onPause(); super.onPause() }

    private fun runBatch1Probes() {
        Log.d("G0-BATCH1", "GL ready, starting Batch 1 probes")
        val glErrBefore = drainGlErrors()
        val cap = GpuCapabilityProbe().probe()
        val fbo = FboProbe().probe(256, 256)
        val glErrAfter = drainGlErrors()
        val pass = glErrBefore == 0 && glErrAfter == 0 && cap.glError == 0 && fbo.glError == 0 &&
                fbo.rgba16fComplete && fbo.rg16fComplete
        Log.d("G0-BATCH1", "GL_VERSION=${cap.glVersion}")
        Log.d("G0-BATCH1", "GLSL_VERSION=${cap.glslVersion}")
        Log.d("G0-BATCH1", "RGBA16F_FBO=${if (fbo.rgba16fComplete) "COMPLETE" else "INCOMPLETE"}")
        Log.d("G0-BATCH1", "RG16F_FBO=${if (fbo.rg16fComplete) "COMPLETE" else "INCOMPLETE"}")
        Log.d("G0-BATCH1", "G0-BATCH1=${if (pass) "PASS" else "FAIL"}")
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        EvidenceWriter(this).writeProbeEvidence(
            date = date,
            commit = "uncommitted",
            capability = cap,
            fbo = fbo,
            glErrorBefore = glErrBefore,
            glErrorAfter = glErrAfter
        )
        runOnUiThread { surface.postDelayed({ finish() }, 200) }
    }

    private fun runOneFixture(id: String) {
        val loader = TestFixtureLoader(this)
        val fixture = loader.load(id)
        val runner = PaintTestRunner(controller)
        val resultsWithBitmaps = mutableListOf<Pair<TestResult, List<Bitmap?>>>()

        // PaintTestRunner 需稍作修改：把 bitmaps 也返回
        runner.runFixtureWithBitmaps(fixture) { result, bitmaps ->
            resultsWithBitmaps += result to bitmaps
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val env = TestEnvironment.from(glVersion, glslVersion, fixture.canvasWidth, fixture.canvasHeight)
            EvidenceWriter(this).write(date, "uncommitted", env, resultsWithBitmaps)
            Log.d("G0-BATCH2", "fixture=${result.fixtureId} pass=${result.pass} failures=${result.failures}")
            Log.d("G0-BATCH2", "metrics=${result.segmentMetrics}")
            Log.d("G0-BATCH2", "lifecycle=${result.lifecycle} extras=${result.extras}")
            runOnUiThread { surface.postDelayed({ finish() }, 300) }
        }
    }

    private fun runAllFixtures() {
        val loader = TestFixtureLoader(this)
        val ids = listOf("T1", "T2", "T3", "T4", "T5", "T6", "T7")
        val allResultsWithBitmaps = mutableListOf<Pair<TestResult, List<Bitmap?>>>()

        fun next(i: Int) {
            if (i >= ids.size) {
                val allPass = allResultsWithBitmaps.all { it.first.pass }
                Log.d("G0-BATCH2",
                    "G0-BATCH2=${if (allPass) "PASS" else "FAIL"} " +
                            "passed=${allResultsWithBitmaps.count { it.first.pass }}/${allResultsWithBitmaps.size}")
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val env = TestEnvironment.from(glVersion, glslVersion, 1024, 1024)
                EvidenceWriter(this).write(date, "uncommitted", env, allResultsWithBitmaps)
                runOnUiThread { surface.postDelayed({ finish() }, 300) }
                return
            }
            val fixture = loader.load(ids[i])
            PaintTestRunner(controller).runFixtureWithBitmaps(fixture) { result, bitmaps ->
                Log.d("G0-BATCH2", "fixture=${result.fixtureId} pass=${result.pass} failures=${result.failures}")
                Log.d("G0-BATCH2", "  metrics=${result.segmentMetrics}")
                Log.d("G0-BATCH2", "  lifecycle=${result.lifecycle} extras=${result.extras}")
                allResultsWithBitmaps += result to bitmaps
                next(i + 1)
            }
        }
        next(0)
    }

    private fun drainGlErrors(): Int {
        var lastErr = 0
        while (true) {
            val e = GLES30.glGetError()
            if (e == GLES30.GL_NO_ERROR) break
            lastErr = e
        }
        return lastErr
    }
}
