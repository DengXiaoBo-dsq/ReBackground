package com.dsq.rebackground.painttest

import android.os.Build
import org.json.JSONObject

data class TestEnvironment(
    val device: String,
    val androidVersion: String,
    val glVersion: String,
    val glslVersion: String,
    val canvasWidth: Int,
    val canvasHeight: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("device", device)
        put("androidVersion", androidVersion)
        put("glVersion", glVersion)
        put("glslVersion", glslVersion)
        put("canvasWidth", canvasWidth)
        put("canvasHeight", canvasHeight)
    }

    companion object {
        fun from(glVersion: String, glslVersion: String, w: Int, h: Int): TestEnvironment =
            TestEnvironment(
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = "API ${Build.VERSION.SDK_INT}",
                glVersion = glVersion,
                glslVersion = glslVersion,
                canvasWidth = w,
                canvasHeight = h
            )
    }
}