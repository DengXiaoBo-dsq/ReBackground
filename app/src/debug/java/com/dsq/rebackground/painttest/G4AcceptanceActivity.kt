package com.dsq.rebackground.painttest

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.dsq.rebackground.paint.brush.BrushDefinition
import com.dsq.rebackground.paint.brush.BrushDynamics
import com.dsq.rebackground.paint.brush.BrushGenerator
import com.dsq.rebackground.paint.brush.DynamicsCurve
import com.dsq.rebackground.paint.brush.SpeedSensor
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.stroke.StrokePoint
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
import kotlin.math.sin

class G4AcceptanceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gate = intent.getStringExtra("gate")?.uppercase(Locale.US) ?: "A"
        val result = when (gate) {
            "A" -> runCurves()
            "B" -> runPressureEndpoints()
            "C" -> runSpeed()
            "D" -> runTilt()
            else -> Result(false, JSONObject().put("error", "unknown gate"))
        }
        writeResult(gate, result)
        Log.d("G4-$gate", "result=${if (result.pass) "PASS" else "FAIL"} metrics=${result.metrics}")
        finish()
    }

    private fun runCurves(): Result {
        val inputs = (0..100).map { it / 100.0 }
        val linearPairs = inputs.map { it to DynamicsCurve.Linear.evaluate(it.toFloat()).toDouble() }
        val linearErrors = linearPairs.map { abs(it.first - it.second) }
        val linearViolations = linearPairs.zipWithNext().count { (a, b) -> b.second < a.second }
        val curveRows = JSONArray()
        val cases = listOf(
            "bezier" to Pair<DynamicsCurve, (Double) -> Double>(
                DynamicsCurve.Bezier(0.25f, 0.1f, 0.25f, 1f),
                { referenceBezier(it, 0.25, 0.1, 0.25, 1.0) },
            ),
            "soft" to Pair<DynamicsCurve, (Double) -> Double>(DynamicsCurve.Soft, { 1.0 - (1.0 - it) * (1.0 - it) }),
            "hard" to Pair<DynamicsCurve, (Double) -> Double>(DynamicsCurve.Hard, { it * it }),
            "inverse" to Pair<DynamicsCurve, (Double) -> Double>(DynamicsCurve.Inverse, { 1.0 - it }),
        )
        var generalPass = true
        cases.forEach { (name, pair) ->
            val errors = inputs.map { abs(pair.first.evaluate(it.toFloat()) - pair.second(it)) }
            val mae = errors.average()
            val max = errors.max()
            generalPass = generalPass && mae <= 0.01 && max <= 0.03
            curveRows.put(JSONObject().put("curve", name).put("mae", mae).put("maxError", max))
        }
        val linearR2 = rSquared(linearPairs)
        val metrics = JSONObject()
            .put("points", inputs.size)
            .put("linearR2", linearR2)
            .put("linearMae", linearErrors.average())
            .put("linearMaxError", linearErrors.max())
            .put("monotonicViolations", linearViolations)
            .put("generalCurves", curveRows)
        return Result(
            linearR2 >= 0.99 && linearErrors.average() <= 0.01 &&
                linearErrors.max() <= 0.03 && linearViolations == 0 && generalPass,
            metrics,
        )
    }

    private fun runPressureEndpoints(): Result {
        val definition = BrushDefinition(
            "g4-pressure", "G4 Pressure", 100f,
            opacity = 0.8f,
            flow = 0.6f,
            dynamics = BrushDynamics(
                minimumDiameterRatio = 0.2f,
                pressureSizeInfluence = 1f,
                pressureOpacityInfluence = 1f,
                minimumOpacityRatio = 0.1f,
                pressureFlowInfluence = 1f,
                minimumFlowRatio = 0.05f,
                speedSmoothingTimeConstantMillis = 0f,
            ),
        )
        val generator = BrushGenerator()
        val low = generator.generate(definition, StrokePoint(Vec2(0f, 0f), 0f, 0f, 0f, 0L))
        val high = generator.generate(definition, StrokePoint(Vec2(0f, 0f), 1f, 0f, 0f, 0L))
        val rows = JSONArray()
        fun row(name: String, actual0: Double, expected0: Double, actual1: Double, expected1: Double) {
            rows.put(JSONObject().put("parameter", name)
                .put("p0", actual0).put("p1", actual1)
                .put("p0RelativeError", relativeError(actual0, expected0))
                .put("p1RelativeError", relativeError(actual1, expected1)))
        }
        row("size", low.stamp.diameterDocumentUnits.toDouble(), 20.0, high.stamp.diameterDocumentUnits.toDouble(), 100.0)
        row("opacity", low.opacityFactor.toDouble(), 0.1, high.opacityFactor.toDouble(), 1.0)
        row("flow", low.flowFactor.toDouble(), 0.05, high.flowFactor.toDouble(), 1.0)
        val maxError = (0 until rows.length()).maxOf { index ->
            val item = rows.getJSONObject(index)
            maxOf(item.getDouble("p0RelativeError"), item.getDouble("p1RelativeError"))
        }
        return Result(maxError <= 0.01, JSONObject().put("parameters", rows).put("maxRelativeError", maxError))
    }

    private fun runSpeed(): Result {
        val durations = listOf(10L, 20L, 40L)
        val expected = listOf(1_000f, 500f, 250f)
        val rows = JSONArray()
        val rawErrors = mutableListOf<Double>()
        val descendingOutputs = mutableListOf<Float>()
        durations.forEachIndexed { index, duration ->
            val sample = SpeedSensor.sample(10f, duration, 1_000f, 0f, 0f)
            rawErrors += abs(sample.rawDocumentUnitsPerSecond - expected[index]).toDouble()
            descendingOutputs += 1f - sample.normalized
            rows.put(JSONObject().put("distance", 10).put("durationMs", duration)
                .put("expectedSpeed", expected[index]).put("actualSpeed", sample.rawDocumentUnitsPerSecond)
                .put("normalized", sample.normalized))
        }
        val ordered = descendingOutputs.reversed()
        val violations = ordered.zipWithNext().count { (a, b) -> b > a + 1e-6f }
        val smoothed = SpeedSensor.sample(10f, 10L, 1_000f, 0f, 35f)
        val duplicateTimestamp = SpeedSensor.sample(10f, 0L, 1_000f, 321f, 35f)
        val pass = rawErrors.max() <= 0.01 && violations == 0 &&
            smoothed.smoothedDocumentUnitsPerSecond.isFinite() &&
            smoothed.smoothedDocumentUnitsPerSecond in 0f..1_000f &&
            duplicateTimestamp.smoothedDocumentUnitsPerSecond == 321f
        return Result(pass, JSONObject().put("cases", rows)
            .put("maxSpeedError", rawErrors.max()).put("monotonicViolations", violations)
            .put("smoothedSpeed", smoothed.smoothedDocumentUnitsPerSecond)
            .put("duplicateTimestampHeldSpeed", duplicateTimestamp.smoothedDocumentUnitsPerSecond))
    }

    private fun runTilt(): Result {
        val definition = BrushDefinition(
            "g4-tilt", "G4 Tilt", 20f,
            dynamics = BrushDynamics(tiltAspectInfluence = 1f, tiltRotationInfluence = 1f),
        )
        val rows = JSONArray()
        val errors = mutableListOf<Double>()
        val aspectErrors = mutableListOf<Double>()
        for (degrees in 0 until 360 step 5) {
            val radians = Math.toRadians(degrees.toDouble())
            val magnitude = 0.75
            val output = BrushGenerator().generate(
                definition,
                StrokePoint(Vec2(0f, 0f), 1f, (cos(radians) * magnitude).toFloat(), (sin(radians) * magnitude).toFloat(), 0L),
            )
            val error = Math.toDegrees(angularError(output.stamp.rotationRadians.toDouble(), radians))
            val aspectError = relativeError(output.stamp.aspectRatio.toDouble(), 1.0 + magnitude)
            errors += error
            aspectErrors += aspectError
            rows.put(JSONObject().put("inputDegrees", degrees)
                .put("outputDegrees", Math.toDegrees(output.stamp.rotationRadians.toDouble()))
                .put("orientationErrorDegrees", error).put("aspectRelativeError", aspectError))
        }
        val mean = errors.average()
        val p99 = errors.sorted()[(errors.size * 0.99).toInt().coerceAtMost(errors.lastIndex)]
        val pass = mean <= 3.0 && p99 <= 3.0 && aspectErrors.max() <= 0.01
        return Result(pass, JSONObject().put("meanOrientationErrorDegrees", mean)
            .put("p99OrientationErrorDegrees", p99).put("maxAspectRelativeError", aspectErrors.max())
            .put("cases", rows))
    }

    private fun writeResult(gate: String, result: Result) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val root = File(getExternalFilesDir(null), "evidence/$date/G4/$gate/uncommitted").also { it.mkdirs() }
        File(root, "metrics.json").writeText(result.metrics.toString(2))
        File(root, "summary.json").writeText(JSONObject().put("gate", "G4-$gate")
            .put("result", if (result.pass) "PASS" else "FAIL").put("metrics", result.metrics).toString(2))
        File(root, "log.txt").writeText("G4-$gate=${if (result.pass) "PASS" else "FAIL"}\n${result.metrics.toString(2)}\n")
    }

    private fun relativeError(actual: Double, expected: Double): Double =
        abs(actual - expected) / maxOf(abs(expected), 1e-12)

    private fun rSquared(points: List<Pair<Double, Double>>): Double {
        val mean = points.map { it.second }.average()
        val residual = points.sumOf { (x, y) -> (y - x) * (y - x) }
        val total = points.sumOf { (_, y) -> (y - mean) * (y - mean) }
        return if (total == 0.0) 1.0 else 1.0 - residual / total
    }

    private fun referenceBezier(x: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        fun cubic(t: Double, a: Double, b: Double): Double {
            val oneMinus = 1.0 - t
            return 3.0 * oneMinus * oneMinus * t * a + 3.0 * oneMinus * t * t * b + t * t * t
        }
        var low = 0.0
        var high = 1.0
        repeat(60) {
            val t = (low + high) * 0.5
            if (cubic(t, x1, x2) < x) low = t else high = t
        }
        return cubic((low + high) * 0.5, y1, y2)
    }

    private fun angularError(actual: Double, expected: Double): Double {
        var difference = abs(actual - expected) % (2.0 * PI)
        if (difference > PI) difference = 2.0 * PI - difference
        return difference
    }

    private data class Result(val pass: Boolean, val metrics: JSONObject)
}
