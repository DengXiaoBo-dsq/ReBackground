package com.dsq.rebackground.paint.brush

/**
 * 笔刷条目（PR-2 后替代 model/BrushItem.kt）
 * 不依赖 ，自包含。
 *
 * [MOD PR-2 Step 1]
 *   新增 material 字段，承载 BrushMaterial 全部参数。
 *   不变量：一旦 tip 是 BrushTip.Texture，texturePath 必须等于 tip.resourceKey。
 *   这条约束保证：
 *     - UI 层（用 texturePath 加载 Bitmap）与
 *     - 渲染层（用 textureResourceKey 查 GL 纹理句柄）
 *   使用完全相同的字符串 key，杜绝"UI 加载了 bitmap 但渲染层找不到"的错配。
 */
data class BrushItem(
    val id: String,
    val displayName: String,
    val group: String,
    val tip: BrushTip,
    val material: BrushMaterial,
    val texturePath: String?,
    val thumbnailPath: String?,
    val defaultDiameter: Float,
    val defaultDynamics: BrushDynamics,
    val defaultOpacity: Float = 0.99f,
    val defaultFlow: Float = 0.5f,
    val isPreset: Boolean
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(defaultDiameter > 0f)
        require(defaultOpacity in 0f..1f)
        require(defaultFlow in 0f..1f)
        if (tip is BrushTip.Texture) {
            require(texturePath == tip.resourceKey) {
                "BrushItem: tip.resourceKey must equal texturePath " +
                        "(tip=${tip.resourceKey}, path=$texturePath)"
            }
        }
        if (texturePath != null) {
            require(texturePath.isNotBlank())
        }
    }
}