package com.dsq.rebackground.painttest.report

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.dsq.rebackground.painttest.TestEnvironment
import com.dsq.rebackground.painttest.TestResult
import com.dsq.rebackground.painttest.gpu.FboProbe
import com.dsq.rebackground.painttest.gpu.GpuCapabilityProbe
import com.dsq.rebackground.painttest.metrics.TestBitmapAnalyzer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EvidenceWriter(private val context: Context) {

    data class Written(val root: File, val results: List<TestResult>, val pass: Boolean)

    /** Writes the device-only G0-02/G0-03 evidence before replay begins. */
    fun writeProbeEvidence(
        date: String,
        commit: String,
        capability: GpuCapabilityProbe.Result,
        fbo: FboProbe.Result,
        glErrorBefore: Int,
        glErrorAfter: Int
    ) {
        val root = root(date, commit)
        val pass = glErrorBefore == 0 && glErrorAfter == 0 && capability.glError == 0 &&
            fbo.glError == 0 && fbo.rgba16fComplete && fbo.rg16fComplete
        val json = JSONObject().apply {
            put("g0ProbePass", pass)
            put("glErrorBefore", glErrorBefore)
            put("glErrorAfter", glErrorAfter)
            put("capability", JSONObject().apply {
                put("glVersion", capability.glVersion)
                put("glslVersion", capability.glslVersion)
                put("maxTextureSize", capability.maxTextureSize)
                put("maxTextureImageUnits", capability.maxTextureImageUnits)
                put("maxCombinedTextureUnits", capability.maxCombinedTextureUnits)
                put("maxRenderbufferSize", capability.maxRenderbufferSize)
                put("glError", capability.glError)
            })
            put("fbo", JSONObject().apply {
                put("rgba16fRenderable", fbo.rgba16fComplete)
                put("rgba16fStatus", fbo.rgba16fStatus)
                put("rg16fRenderable", fbo.rg16fComplete)
                put("rg16fStatus", fbo.rg16fStatus)
                put("glError", fbo.glError)
            })
        }
        File(root, "probes.json").writeText(json.toString(2))
    }

    fun write(
        date: String,
        commit: String,
        env: TestEnvironment,
        resultsWithBitmaps: List<Pair<TestResult, List<Bitmap?>>>
    ): Written {
        val root = root(date, commit)

        val replayPass = resultsWithBitmaps.size == 7 && resultsWithBitmaps.all { it.first.pass }
        val probes = File(root, "probes.json").takeIf { it.isFile }?.let {
            runCatching { JSONObject(it.readText()) }.getOrNull()
        }
        val probePass = probes?.optBoolean("g0ProbePass", false) ?: false
        val allPass = replayPass && probePass

        val summary = JSONObject().apply {
            put("gate", "G0")
            put("specVersion", "G0 Acceptance Spec 1.0")
            put("commit", commit)
            put("date", date)
            put("device", env.device)
            put("glVersion", env.glVersion)
            put("result", if (allPass) "PASS" else "FAIL")
            put("testsTotal", resultsWithBitmaps.size)
            put("testsPassed", resultsWithBitmaps.count { it.first.pass })
            put("testsFailed", resultsWithBitmaps.count { !it.first.pass })
            put("replayPass", replayPass)
            put("probePass", probePass)
            put("probes", probes ?: JSONObject.NULL)
            put("environment", env.toJson())
        }

        val testsJson = JSONArray()
        for ((r, bitmaps) in resultsWithBitmaps) {
            val testDir = File(root, r.fixtureId)
            if (!testDir.exists()) testDir.mkdirs()

            // 逐 segment 保存
            val segPngs = JSONArray()
            bitmaps.forEachIndexed { i, bmp ->
                if (bmp != null) {
                    val f = File(testDir, "seg$i.png")
                    TestBitmapAnalyzer.savePng(bmp, f.absolutePath)
                    segPngs.put("seg$i.png")
                } else {
                    segPngs.put(JSONObject.NULL)
                }
            }
            // final.png
            val lastBmp = bitmaps.lastOrNull()
            if (lastBmp != null) {
                TestBitmapAnalyzer.savePng(lastBmp, File(testDir, "final.png").absolutePath)
            }

            // metrics.json
            val metricsJson = JSONObject()
            val arr = JSONArray()
            r.segmentMetrics.forEachIndexed { i, m ->
                val obj = JSONObject()
                for ((k, v) in m) obj.put(k, v)
                arr.put(JSONObject().apply { put("segment", i); put("metrics", obj) })
            }
            metricsJson.put("segments", arr)
            metricsJson.put("lifecycle", JSONObject(r.lifecycle as Map<*, *>))
            val extrasObj = JSONObject()
            for ((k, v) in r.extras) extrasObj.put(k, v)
            metricsJson.put("extras", extrasObj)
            File(testDir, "metrics.json").writeText(metricsJson.toString(2))

            testsJson.put(JSONObject().apply {
                put("id", r.fixtureId)
                put("pass", r.pass)
                put("failures", JSONArray(r.failures))
                put("metrics", metricsJson)
                put("segPngs", segPngs)
                put("finalPng", if (lastBmp != null) "final.png" else JSONObject.NULL)
            })
        }
        summary.put("tests", testsJson)

        File(root, "summary.json").writeText(summary.toString(2))
        Log.d("G0-BATCH2", "evidence written: ${root.absolutePath}")

        return Written(root, resultsWithBitmaps.map { it.first }, allPass)
    }

    private fun root(date: String, commit: String): File =
        File(context.getExternalFilesDir(null), "evidence/$date/G0/$commit").also { root ->
            if (!root.exists()) root.mkdirs()
        }
}
