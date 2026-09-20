package com.dsq.rebackground.paint.acceptance

/** Final acceptance scenarios and measured evidence required before the engine can be called production-ready. */
data class AcceptanceScenario(
    val id: String,
    val name: String,
    val category: String,
    val requiredMetrics: List<String>
)

data class AcceptanceReport(
    val scenario: AcceptanceScenario,
    val passed: Boolean,
    val frameTimeMillis: Double,
    val inputLatencyMillis: Double,
    val notes: String
)

object FinalAcceptanceMatrix {
    val scenarios: List<AcceptanceScenario> = listOf(
        AcceptanceScenario("finger-slow", "普通手指慢速", "input", listOf("input_latency_ms")),
        AcceptanceScenario("finger-fast", "普通手指高速", "input", listOf("input_latency_ms")),
        AcceptanceScenario("stylus-pressure", "主动电容笔不同压力", "input", listOf("pressure", "input_latency_ms")),
        AcceptanceScenario("stylus-tilt", "主动电容笔倾斜", "input", listOf("tilt", "input_latency_ms")),
        AcceptanceScenario("turn-sharp", "急转弯", "input", listOf("frame_time_ms")),
        AcceptanceScenario("long-stroke", "长笔画", "history", listOf("frame_time_ms", "memory_bytes")),
        AcceptanceScenario("short-stroke", "短笔画", "history", listOf("frame_time_ms")),
        AcceptanceScenario("continuous", "连续书写", "history", listOf("frame_time_ms", "cpu_usage")),
        AcceptanceScenario("zoom", "缩放", "viewport", listOf("frame_time_ms")),
        AcceptanceScenario("rotate", "旋转", "viewport", listOf("frame_time_ms")),
        AcceptanceScenario("pan", "平移", "viewport", listOf("frame_time_ms")),
        AcceptanceScenario("4k", "4K画布", "performance", listOf("frame_time_ms", "gpu_time_ms", "memory_bytes")),
        AcceptanceScenario("large-area", "大面积涂抹", "performance", listOf("frame_time_ms", "gpu_time_ms")),
        AcceptanceScenario("wet-ink", "湿墨", "pigment", listOf("frame_time_ms")),
        AcceptanceScenario("dry-brush", "干笔", "pigment", listOf("frame_time_ms"))
    )

    fun byId(id: String): AcceptanceScenario = scenarios.first { it.id == id }
}
