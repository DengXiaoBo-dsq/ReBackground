package com.dsq.rebackground.paint.brush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Brush texture loader.
 *
 * [MOD PR-2 Step 1]
 *   统一 key 约定：
 *     - BrushItem.texturePath   → UI 层加载 Bitmap 的 key
 *     - BrushTip.Texture.resourceKey → 渲染层查 GL 纹理句柄的 key
 *     - 两者必须相等（BrushItem.init 中已 require 校验）
 *   loadAllTextures() 返回的 Map key 直接使用 texturePath，
 *   与渲染层 key 完全一致，无需再做转换。
 */
object BrushLoader {

    /** 从 texturePath 加载 bitmap。支持 "asset://..." 与绝对路径。 */
    fun loadTextureFromPath(context: Context, path: String): Bitmap? {
        return try {
            when {
                path.startsWith("asset://") -> {
                    val assetPath = path.removePrefix("asset://")
                    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
                }
                path.startsWith("/") -> BitmapFactory.decodeFile(path)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 批量加载。
     * 返回 Map<texturePath, Bitmap>。
     * 程序化笔刷（texturePath == null）自动跳过。
     * 加载失败的单个笔刷静默跳过（不抛异常，不影响其他笔刷）。
     */
    fun loadAllTextures(context: Context, items: List<BrushItem>): Map<String, Bitmap> {
        val result = HashMap<String, Bitmap>()
        for (item in items) {
            val path = item.texturePath ?: continue
            val bmp = loadTextureFromPath(context, path) ?: continue
            result[path] = bmp
        }
        return result
    }

    /** 生成缩略图。 */
    fun generateThumbnail(bitmap: Bitmap, size: Int = 96): Bitmap =
        Bitmap.createScaledBitmap(bitmap, size, size, true)
}