package com.dsq.rebackground.paint.brush

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

object BrushLibrary {

private const val TAG = "BrushLibrary"
private const val ASSET_DIR = "brushes"
private const val CUSTOM_CONFIG_DIR = "/storage/emulated/0/ReMoveBg-Config/brush_configs"

// ============================================================
// 内置笔刷
// ============================================================

fun presetBrushes(context: Context): List<BrushItem> {
val result = mutableListOf<BrushItem>()

// 1. 硬编码的程序化笔刷
result.add(createElectricBrush())

// 2. 动态扫描 assets/brushes/
val files = try {
context.assets.list(ASSET_DIR) ?: emptyArray()
} catch (e: Exception) {
Log.e(TAG, "Failed to list assets/$ASSET_DIR", e)
emptyArray()
}

files.sorted().forEach { fileName ->
if (!fileName.endsWith(".png", ignoreCase = true)) return@forEach
val name = fileName.substringBeforeLast('.')
// 避免与硬编码电子画笔冲突
if (name.equals("electric", ignoreCase = true)) return@forEach
result.add(createPresetFromAsset(name))
}

return result
}

// ============================================================
// 用户自定义笔刷
// ============================================================

fun userBrushes(context: Context): List<BrushItem> {
val dir = File(CUSTOM_CONFIG_DIR)
if (!dir.exists() || !dir.isDirectory) return emptyList()

val jsonFiles = dir.listFiles { f -> f.isFile && f.extension == "json" }
?: return emptyList()

return jsonFiles.sortedBy { it.name }.mapNotNull { jsonFile ->
try {
val json = JSONObject(jsonFile.readText())
val data = CustomBrushData.fromJson(json)

if (!File(data.texturePath).exists()) {
Log.w(TAG, "Skip ${data.id}: texture missing at ${data.texturePath}")
return@mapNotNull null
}

data.toBrushItem()
} catch (e: Exception) {
Log.e(TAG, "Failed to parse ${jsonFile.name}", e)
null
}
}
}

// ============================================================
// 全部
// ============================================================

fun allBrushes(context: Context): List<BrushItem> =
presetBrushes(context) + userBrushes(context)

// ============================================================
// 内置笔刷构造
// ============================================================

private fun createElectricBrush(): BrushItem = BrushItem(
id = "preset_electric",
displayName = "电子画笔",
group = "内置",
tip = BrushTip.Round,
material = BrushMaterial(),
texturePath = null,
thumbnailPath = null,
defaultDiameter = 8f,
defaultDynamics = BrushDynamics(),
    defaultOpacity = 0.99f,
    defaultFlow = 1.0f,   // [MOD PR-2.6] 电子笔默认全浓度
isPreset = true
)

private fun createPresetFromAsset(name: String): BrushItem {
val lower = name.lowercase()
val path = "asset://$ASSET_DIR/$name.png"
return BrushItem(
id = "preset_$lower",
displayName = name.replaceFirstChar { it.uppercase() },
group = "内置",
tip = BrushTip.Texture(resourceKey = path, aspectRatio = 1f),
material = materialFor(lower),
texturePath = path,
thumbnailPath = null,
defaultDiameter = diameterFor(lower),
defaultDynamics = dynamicsFor(lower),
    defaultOpacity = 0.99f,
    defaultFlow = 1.0f,   // [MOD PR-2.6] 电子笔默认全浓度
isPreset = true
)
}

// ============================================================
// 参数表（从  BrushesRepository 迁移）
// ============================================================

private fun materialFor(name: String): BrushMaterial = when (name) {
"pencil" -> BrushMaterial(
spacingRatio = 0.08f,
edgeHardness = 0.6f,
rotationRandomness = 1f,
bristleDensity = 0.9f,
paperGrainAffinity = 0.7f
)
"pen" -> BrushMaterial(spacingRatio = 0.05f, edgeHardness = 0.9f)
"calligraphy" -> BrushMaterial(spacingRatio = 0.02f, fixedRotationDegrees = 45f)
"airbrush" -> BrushMaterial(spacingRatio = 0.05f, edgeHardness = 0.2f)
"marker" -> BrushMaterial(spacingRatio = 0.08f, edgeHardness = 0.9f)
else -> BrushMaterial()
}

private fun dynamicsFor(name: String): BrushDynamics = when (name) {
"pencil" -> BrushDynamics(
minimumDiameterRatio = 0.25f,
speedSizeInfluence = 0.35f,
tiltAspectInfluence = 0.35f
)
"marker" -> BrushDynamics(
minimumDiameterRatio = 0.35f,
speedSizeInfluence = 0.08f
)
"airbrush" -> BrushDynamics(
minimumDiameterRatio = 0.5f,
pressureSizeInfluence = 0.8f
)
else -> BrushDynamics()
}

private fun diameterFor(name: String): Float = when (name) {
"pencil" -> 0.1f * CustomBrushData.DIAMETER_BASELINE
"pen" -> 0.1f * CustomBrushData.DIAMETER_BASELINE
"calligraphy" -> 0.2f * CustomBrushData.DIAMETER_BASELINE
"airbrush" -> 0.2f * CustomBrushData.DIAMETER_BASELINE
"marker" -> 0.4f * CustomBrushData.DIAMETER_BASELINE
else -> 0.2f * CustomBrushData.DIAMETER_BASELINE
}

private fun flowFor(name: String): Float = when (name) {
"marker" -> 0.2f
else -> 0.8f
}
}