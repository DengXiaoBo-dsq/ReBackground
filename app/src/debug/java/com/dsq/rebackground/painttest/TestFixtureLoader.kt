package com.dsq.rebackground.painttest

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TestFixtureLoader(private val context: Context) {

    fun load(fixtureId: String): TestFixture {
        val path = "fixtures/G0/$fixtureId.json"
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        val json = JSONObject(text)

        val segsArr = json.getJSONArray("segments")
        val segments = ArrayList<TestSegment>(segsArr.length())
        for (i in 0 until segsArr.length()) {
            val s = segsArr.getJSONObject(i)
            val ptsArr = s.getJSONArray("points")
            val pts = ArrayList<TestPoint>(ptsArr.length())
            for (j in 0 until ptsArr.length()) {
                val p = ptsArr.getJSONArray(j)
                pts += TestPoint(
                    x = p.getDouble(0).toFloat(),
                    y = p.getDouble(1).toFloat(),
                    pressure = if (p.length() > 2) p.getDouble(2).toFloat() else 1f,
                    tiltX = if (p.length() > 3) p.getDouble(3).toFloat() else 0f,
                    tiltY = if (p.length() > 4) p.getDouble(4).toFloat() else 0f
                )
            }
            segments += TestSegment(
                colorHex = s.optInt("colorHex", 0xFF0000),
                opacity = s.optDouble("opacity", 1.0).toFloat(),
                flow = s.optDouble("flow", 1.0).toFloat(),
                points = pts
            )
        }

        val expectedObj = json.optJSONObject("expected")
        val expected: Map<String, Any?> = if (expectedObj != null) {
            expectedObj.keys().asSequence().associateWith { key ->
                jsonValueToKotlin(expectedObj.get(key))
            }
        } else emptyMap()

        return TestFixture(
            id = json.getString("id"),
            canvasWidth = json.optInt("canvasWidth", 1024),
            canvasHeight = json.optInt("canvasHeight", 1024),
            seed = json.optLong("seed", 1L),
            timestampStepMs = json.optLong("timestampStepMs", 4L),
            idleWaitMs = json.optLong("idleWaitMs", 0L),
            segments = segments,
            expected = expected
        )
    }

    /**
     * Fixtures cross the JSON boundary once.  Do not leak JSONArray/JSONObject into
     * metric code: their Kotlin casts silently fail and can turn an invalid fixture
     * into an unrelated default assertion.
     */
    private fun jsonValueToKotlin(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { key ->
            jsonValueToKotlin(value.get(key))
        }
        is JSONArray -> List(value.length()) { index -> jsonValueToKotlin(value.get(index)) }
        JSONObject.NULL -> null
        else -> value
    }
}
