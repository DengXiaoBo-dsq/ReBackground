package com.dsq.rebackground.paint.brush

import org.json.JSONObject

/**
 * 用户自定义笔刷的元数据。
 * PR-2 迁入 paint/brush 包，不再依赖 。
 *
 * JSON 格式（磁盘上的 *.json）**完全保持不变**：
 *   id / name / group / texturePath / size / flow / opacity
 *   / spacing / rotation / rotationRandomness / createdAt
 * 保证用户已有自定义笔刷可无缝加载。
 *
 * 换算约定（D2 决策）：
 *   size: 0~1 → BrushItem.defaultDiameter = size * DIAMETER_BASELINE
 *   基准 512 像素，与 BrushTextureHelper.MAX_BRUSH_SHORT_SIDE 一致。
 */
data class CustomBrushData(
    val id: String,
    val name: String,
    val group: String,
    val texturePath: String,
    val size: Float,
    val flow: Float,
    val opacity: Float,
    val spacing: Float,
    val rotation: Int,
    val rotationRandomness: Float,
    val createdAt: Long = System.currentTimeMillis()
) {

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("group", group)
            put("texturePath", texturePath)
            put("size", size.toDouble())
            put("flow", flow.toDouble())
            put("opacity", opacity.toDouble())
            put("spacing", spacing.toDouble())
            put("rotation", rotation)
            put("rotationRandomness", rotationRandomness.toDouble())
            put("createdAt", createdAt)
        }
    }

    /**
     * 转为 BrushItem（加载用户笔刷）。
     *
     * 关键点：
     *   - texturePath 直接作为 tip.resourceKey（满足 BrushItem 不变量）
     *   - spacing 至少 0.02f，避免 BrushMaterial 的 spacingRatio > 0 校验失败
     *   - opacity / flow 至少 0.01f，避免 BrushItem 的 0f..1f 校验失败
     */
    fun toBrushItem(): BrushItem {
        return BrushItem(
            id = id,
            displayName = name,
            group = group,
            tip = BrushTip.Texture(
                resourceKey = texturePath,
                aspectRatio = 1f
            ),
            material = BrushMaterial(
                spacingRatio = spacing.coerceAtLeast(0.02f),
                rotationRandomness = rotationRandomness.coerceIn(0f, 1f),
                fixedRotationDegrees = rotation.toFloat()
            ),
            texturePath = texturePath,
            thumbnailPath = null,
            defaultDiameter = (size * DIAMETER_BASELINE).coerceAtLeast(1f),
            defaultDynamics = BrushDynamics(),
            defaultOpacity = opacity.coerceIn(0.01f, 1f),
            defaultFlow = flow.coerceIn(0.01f, 1f),
            isPreset = false
        )
    }

    companion object {
        /** D2 决策：size 0~1 映射到 512 像素 */
        const val DIAMETER_BASELINE = 512f

        fun fromJson(json: JSONObject): CustomBrushData {
            return CustomBrushData(
                id = json.getString("id"),
                name = json.getString("name"),
                group = json.getString("group"),
                texturePath = json.getString("texturePath"),
                size = json.getDouble("size").toFloat(),
                flow = json.getDouble("flow").toFloat(),
                opacity = json.getDouble("opacity").toFloat(),
                spacing = json.getDouble("spacing").toFloat(),
                rotation = json.getInt("rotation"),
                rotationRandomness = json.getDouble("rotationRandomness").toFloat(),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }

        /**
         * 从 BrushItem 反推 CustomBrushData（仅限非预设笔刷）。
         * 用于编辑器保存时构造数据。
         */
        fun fromBrushItem(item: BrushItem): CustomBrushData? {
            if (item.isPreset) return null
            val path = item.texturePath ?: return null
            return CustomBrushData(
                id = item.id,
                name = item.displayName,
                group = item.group,
                texturePath = path,
                size = (item.defaultDiameter / DIAMETER_BASELINE).coerceIn(0f, 1f),
                flow = item.defaultFlow,
                opacity = item.defaultOpacity,
                spacing = item.material.spacingRatio,
                rotation = item.material.fixedRotationDegrees.toInt(),
                rotationRandomness = item.material.rotationRandomness
            )
        }
    }
}