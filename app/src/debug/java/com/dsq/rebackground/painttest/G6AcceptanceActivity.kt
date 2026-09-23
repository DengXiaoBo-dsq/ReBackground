package com.dsq.rebackground.painttest

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.wet.WetCoreConfig
import com.dsq.rebackground.paint.wet.WetCoreSimulator
import com.dsq.rebackground.paint.wet.WetRgb
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Runs each G6 numerical gate on ART and persists reproducible device evidence. */
class G6AcceptanceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gate = intent.getStringExtra("gate")?.uppercase(Locale.US) ?: "A"
        val result = when (gate) {
            "A" -> conservation()
            "B" -> advection()
            "C" -> cfl()
            "D" -> diffusion()
            "E" -> absorption()
            "F" -> finite()
            "G" -> budget()
            "H" -> deposition()
            "I" -> wetOnDry()
            "J" -> wetOnWet()
            "K" -> wetness()
            else -> Result(false, JSONObject().put("error", "unknown gate"))
        }
        write(gate, result)
        Log.d("G6-$gate", "result=${if (result.pass) "PASS" else "FAIL"} metrics=${result.metrics}")
        finish()
    }

    private fun conservation(): Result {
        val s = WetCoreSimulator(48, 48, base()); s.seedCell(24, 24, 1f); val initial = s.waterMass()
        val values = linkedMapOf<Int, Double>()
        listOf(100, 500, 1000).forEach { target -> while (s.metrics().fixedSteps < target) s.stepFixed(); values[target] = rel(initial, s.waterMass()) }
        return Result(values[100]!! <= .01 && values[500]!! <= .015 && values[1000]!! <= .02, JSONObject(values.mapKeys { "step${it.key}RelativeMassError" }))
    }

    private fun advection(): Result {
        val s = WetCoreSimulator(80, 32, base()); blob(s, 20, 16, 4); val start = s.centroidOfWater(); s.setUniformVelocity(Vec2(.6f, 0f)); repeat(60) { s.stepFixed() }
        val error = s.centroidOfWater().distanceTo(Vec2(start.x + .6f, start.y))
        return Result(error <= .01 && rel(1.0, s.waterMass()) <= .02, JSONObject().put("centroidErrorCells", error).put("massResidual", rel(1.0, s.waterMass())))
    }

    private fun cfl(): Result {
        val safe = WetCoreSimulator(8, 8, base()); safe.setUniformVelocity(Vec2(30f, 0f))
        val warned = WetCoreSimulator(8, 8, base()); warned.setUniformVelocity(Vec2(90f, 0f))
        return Result(safe.cflNumber() <= 1f && !safe.metrics().accuracyWarning && warned.metrics().accuracyWarning, JSONObject().put("safeCfl", safe.cflNumber()).put("warningCfl", warned.cflNumber()).put("accuracyWarning", warned.metrics().accuracyWarning))
    }

    private fun diffusion(): Result {
        val s = WetCoreSimulator(48, 48, base(waterDiffusion = .2f)); s.seedCell(24, 24, 1f); val mass = s.waterMass(); val peak = s.peakWater(); val variance = s.varianceOfWater(); val center = s.centroidOfWater(); repeat(300) { s.stepFixed() }
        val massResidual = rel(mass, s.waterMass()); val centerError = s.centroidOfWater().distanceTo(center)
        return Result(massResidual <= .01 && s.peakWater() < peak && s.varianceOfWater() > variance && centerError <= .01f, JSONObject().put("massResidual", massResidual).put("peakInitial", peak).put("peakFinal", s.peakWater()).put("varianceInitial", variance).put("varianceFinal", s.varianceOfWater()).put("centroidError", centerError))
    }

    private fun absorption(): Result {
        val rate = .4f; val s = WetCoreSimulator(8, 8, base(absorption = rate)); s.seedCell(4, 4, 1f)
        val actual = (0..120).map { step -> while (s.metrics().fixedSteps < step.toLong()) s.stepFixed(); s.waterMass() }
        val expected = actual.indices.map { exp(-rate * it * s.config.fixedDt).toDouble() }
        val maxError = actual.indices.maxOf { abs(actual[it] - expected[it]) }
        return Result(maxError <= 1e-5, JSONObject().put("maxAnalyticError", maxError).put("configuredRate", rate))
    }

    private fun finite(): Result {
        val s = WetCoreSimulator(32, 32, WetCoreConfig(waterDiffusion = .22f, pigmentDiffusion = .22f, absorptionRate = .7f, evaporationRate = .4f, depositionRate = 1.4f)); blob(s, 16, 16, 5, WetRgb(1f, .4f, .1f)); s.setUniformVelocity(Vec2(.7f, -.3f)); repeat(1000) { s.stepFixed() }; val m = s.metrics()
        return Result(m.negativeStateViolations == 0 && m.nanCount == 0 && m.infCount == 0, JSONObject().put("negativeStateViolations", m.negativeStateViolations).put("nanCount", m.nanCount).put("infCount", m.infCount))
    }

    private fun budget(): Result {
        val s = WetCoreSimulator(32, 32, WetCoreConfig(absorptionRate = .3f, evaporationRate = .2f, depositionRate = 1f)); s.injectCell(16, 16, 1f, WetRgb(.7f, .2f, .1f)); repeat(360) { s.stepFixed() }; val m = s.metrics()
        return Result(m.waterBudgetResidual <= .01 && m.pigmentBudgetResidual <= .01, JSONObject().put("waterBudgetResidual", m.waterBudgetResidual).put("pigmentBudgetResidual", m.pigmentBudgetResidual))
    }

    private fun deposition(): Result {
        val s = WetCoreSimulator(16, 16, WetCoreConfig(depositionRate = 2f)); s.seedCell(8, 8, 1f, WetRgb(1f, 0f, 0f)); val mobile = s.mobilePigmentMass(); repeat(180) { s.stepFixed() }
        return Result(s.mobilePigmentMass() < mobile && s.boundPigmentMass() > 0.0 && s.metrics().pigmentBudgetResidual <= .01, JSONObject().put("mobileInitial", mobile).put("mobileFinal", s.mobilePigmentMass()).put("boundFinal", s.boundPigmentMass()).put("budgetResidual", s.metrics().pigmentBudgetResidual))
    }

    private fun wetOnDry(): Result {
        val s = WetCoreSimulator(20, 20, WetCoreConfig(absorptionRate = .2f, evaporationRate = .1f, depositionRate = 1.2f)); s.injectCell(10, 10, .8f, WetRgb(.2f, .4f, .9f)); repeat(240) { s.stepFixed() }; val m = s.metrics()
        return Result(s.boundPigmentMass() > 0.0 && m.waterBudgetResidual <= .01 && m.pigmentBudgetResidual <= .01, JSONObject().put("boundMass", s.boundPigmentMass()).put("waterBudgetResidual", m.waterBudgetResidual).put("pigmentBudgetResidual", m.pigmentBudgetResidual))
    }

    private fun wetOnWet(): Result {
        val s = WetCoreSimulator(8, 8, base()); s.injectCell(4, 4, 1f, WetRgb(1f, 0f, 0f), Vec2(2f, 0f)); s.injectCell(4, 4, 3f, WetRgb(0f, 0f, 1f), Vec2(-1f, 0f)); val velocity = s.velocityAt(4, 4)
        return Result(abs(velocity.x + .25f) <= 1e-6f && abs(s.mobilePigmentMass() - 2.0) <= 1e-6, JSONObject().put("mergedVelocityX", velocity.x).put("mobilePigmentMass", s.mobilePigmentMass()))
    }

    private fun wetness(): Result {
        val s = WetCoreSimulator(16, 16, WetCoreConfig(absorptionRate = .2f, evaporationRate = .1f)); s.seedCell(8, 8, 1f); var prior = s.wetnessAt(8, 8); var violations = 0
        repeat(300) { s.stepFixed(); val current = s.wetnessAt(8, 8); if (current > prior + 1e-4f) violations++; prior = current }
        return Result(violations == 0, JSONObject().put("monotonicViolations", violations).put("finalWetness", prior))
    }

    private fun base(waterDiffusion: Float = 0f, absorption: Float = 0f) = WetCoreConfig(
        waterDiffusion = waterDiffusion,
        pigmentDiffusion = 0f,
        absorptionRate = absorption,
        evaporationRate = 0f,
        depositionRate = 0f,
    )
    private fun blob(s: WetCoreSimulator, cx: Int, cy: Int, radius: Int, pigment: WetRgb = WetRgb.Transparent) {
        val rows = mutableListOf<Triple<Int, Int, Float>>(); var total = 0f
        for (y in cy - radius..cy + radius) for (x in cx - radius..cx + radius) { val dx = (x - cx).toFloat(); val dy = (y - cy).toFloat(); val w = kotlin.math.exp(-(dx * dx + dy * dy) / (2f * radius * radius)); rows += Triple(x, y, w); total += w }
        rows.forEach { (x, y, w) -> s.seedCell(x, y, w / total, WetRgb(pigment.red * w / total, pigment.green * w / total, pigment.blue * w / total)) }
    }
    private fun rel(expected: Double, actual: Double) = abs(expected - actual) / maxOf(abs(expected), 1e-12)
    private fun write(gate: String, result: Result) { val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()); val root = File(getExternalFilesDir(null), "evidence/$date/G6/$gate/uncommitted").also { it.mkdirs() }; File(root, "metrics.json").writeText(result.metrics.toString(2)); File(root, "summary.json").writeText(JSONObject().put("gate", "G6-$gate").put("result", if (result.pass) "PASS" else "FAIL").put("metrics", result.metrics).toString(2)); File(root, "log.txt").writeText("G6-$gate=${if (result.pass) "PASS" else "FAIL"}\n${result.metrics}\n") }
    private data class Result(val pass: Boolean, val metrics: JSONObject)
}
