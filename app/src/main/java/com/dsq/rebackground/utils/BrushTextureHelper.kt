package com.dsq.rebackground.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.dsq.rebackground.paint.brush.BrushItem
import com.dsq.rebackground.paint.brush.CustomBrushData
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 画笔纹理管理工具（PR-2 Step 2 已剥离 ）
 *
 * 职责：
 *   - 合规检查、缩略图生成
 *   - 导入 PNG（importBrushFromUri）
 *   - 删除画笔（deleteBrush）
 *   - 分组管理（getGroups / createGroup / deleteGroup / renameGroup）
 *   - 自定义画笔 JSON 配置管理（saveCustomBrush / loadAllCustomBrushData / deleteCustomBrushConfig）
 *
 * 已移除：
 *   - loadPresetBrushes / loadCustomBrushes → 调用方改用 BrushLibrary.allBrushes(context)
 *   - import BrushesRepository / BrushConfig / BrushStamp
 */
object BrushTextureHelper {
    private const val TAG = "BrushTextureHelper"

    // ========== 路径常量 ==========
    private const val BASE_CONFIG_DIR = "/storage/emulated/0/ReMoveBg-Config"
    private const val PRESET_ASSET_DIR = "brushes"
    private const val CUSTOM_BRUSH_ROOT = "$BASE_CONFIG_DIR/brushes"
    private const val THUMB_CACHE_DIR = "$BASE_CONFIG_DIR/brush_thumbs"
    private const val CONFIG_DIR = "$BASE_CONFIG_DIR/brush_configs"

    // ========== 尺寸常量 ==========
    const val MAX_BRUSH_SHORT_SIDE = 512
    const val THUMB_LONG_SIDE = 80

    // ========================= 合规检查 =========================
    data class ValidationResult(
        val valid: Boolean,
        val message: String,
        val shouldResize: Boolean = false,
        val width: Int = 0,
        val height: Int = 0
    )

    fun validateBrushFile(file: File): ValidationResult {
        if (!file.extension.equals("png", ignoreCase = true)) {
            return ValidationResult(false, "仅支持 PNG 格式")
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return ValidationResult(false, "无效的 PNG 图片")
        }
        val w = options.outWidth
        val h = options.outHeight
        val shortSide = minOf(w, h)
        if (shortSide > MAX_BRUSH_SHORT_SIDE) {
            return ValidationResult(
                valid = true,
                message = "纹理尺寸过大（${w}x${h}），将自动缩放到 ${MAX_BRUSH_SHORT_SIDE}px",
                shouldResize = true,
                width = w,
                height = h
            )
        }
        return ValidationResult(valid = true, message = "合规")
    }

    fun resizeBrushTexture(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val shortSide = minOf(w, h)
        if (shortSide <= MAX_BRUSH_SHORT_SIDE) return bitmap
        val scale = MAX_BRUSH_SHORT_SIDE.toFloat() / shortSide
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    // ========================= 缩略图生成 =========================
    fun generateThumbnail(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longSide = maxOf(w, h)
        val scale = THUMB_LONG_SIDE.toFloat() / longSide
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    fun generateThumbnailFile(originalFile: File, context: Context): String? {
        try {
            val thumbDir = File(THUMB_CACHE_DIR)
            if (!thumbDir.exists()) thumbDir.mkdirs()
            val thumbFile = File(thumbDir, "${originalFile.nameWithoutExtension}_thumb.png")
            if (thumbFile.exists() && thumbFile.lastModified() >= originalFile.lastModified()) {
                return thumbFile.absolutePath
            }
            val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath) ?: return null
            val thumbBitmap = generateThumbnail(bitmap)
            FileOutputStream(thumbFile).use { fos ->
                thumbBitmap.compress(Bitmap.CompressFormat.PNG, 80, fos)
            }
            bitmap.recycle()
            thumbBitmap.recycle()
            return thumbFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "生成缩略图失败", e)
            return null
        }
    }

    // ========================= 导入自定义画笔 =========================
    fun importBrushFromUri(context: Context, uri: Uri, groupName: String, brushName: String): String? {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.png")
        try {
            FileOutputStream(tempFile).use { fos ->
                inputStream.copyTo(fos)
            }
            inputStream.close()
        } catch (e: IOException) {
            Log.e(TAG, "保存临时文件失败", e)
            return null
        }

        val result = validateBrushFile(tempFile)
        if (!result.valid) {
            tempFile.delete()
            return null
        }

        if (result.shouldResize) {
            val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath) ?: return null
            val resized = resizeBrushTexture(bitmap)
            FileOutputStream(tempFile).use { fos ->
                resized.compress(Bitmap.CompressFormat.PNG, 90, fos)
            }
            bitmap.recycle()
            resized.recycle()
        }

        val groupDir = File(CUSTOM_BRUSH_ROOT, groupName)
        if (!groupDir.exists()) groupDir.mkdirs()
        val uniqueId = "${groupName}_${System.currentTimeMillis()}_$brushName"
        val destFile = File(groupDir, "$uniqueId.png")
        tempFile.copyTo(destFile, overwrite = true)
        tempFile.delete()
        generateThumbnailFile(destFile, context)
        return uniqueId
    }

    // ========================= 删除画笔 =========================
    fun deleteBrush(context: Context, brushItem: BrushItem): Boolean {
        if (brushItem.isPreset) return false
        val path = brushItem.texturePath ?: return false
        val file = File(path)
        val thumbFile = brushItem.thumbnailPath?.let { File(it) }
        var success = true
        if (file.exists()) success = file.delete()
        if (thumbFile != null && thumbFile.exists()) {
            if (!thumbFile.delete()) success = false
        }
        // 删除关联的配置 JSON
        deleteCustomBrushConfig(brushItem.id)
        return success
    }

    // ========================= 分组管理 =========================
    fun getGroups(context: Context): List<String> {
        val groups = mutableListOf("内置")
        val root = File(CUSTOM_BRUSH_ROOT)
        if (root.exists()) {
            root.listFiles()?.filter { it.isDirectory }?.forEach {
                groups.add(it.name)
            }
        }
        return groups
    }

    fun createGroup(groupName: String): Boolean {
        if (groupName.isBlank()) return false
        val groupDir = File(CUSTOM_BRUSH_ROOT, groupName)
        return groupDir.mkdirs()
    }

    fun deleteGroup(groupName: String): Boolean {
        if (groupName == "内置") return false
        val groupDir = File(CUSTOM_BRUSH_ROOT, groupName)
        if (!groupDir.exists()) return true
        return groupDir.deleteRecursively()
    }

    fun renameGroup(oldName: String, newName: String): Boolean {
        if (oldName == "内置" || newName.isBlank()) return false
        val oldDir = File(CUSTOM_BRUSH_ROOT, oldName)
        val newDir = File(CUSTOM_BRUSH_ROOT, newName)
        if (!oldDir.exists() || newDir.exists()) return false
        return oldDir.renameTo(newDir)
    }

    fun getCustomBrushRoot(): String = CUSTOM_BRUSH_ROOT
    fun getThumbCacheDir(): String = THUMB_CACHE_DIR

    // ========================= 自定义画笔 JSON 配置管理 =========================

    fun saveCustomBrush(data: CustomBrushData, context: Context): Boolean {
        return try {
            val dir = File(CONFIG_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${data.id}.json")
            file.writeText(data.toJson().toString())
            Log.d(TAG, "Custom brush saved: ${data.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Save custom brush failed", e)
            false
        }
    }

    fun loadAllCustomBrushData(context: Context): List<CustomBrushData> {
        val dir = File(CONFIG_DIR)
        if (!dir.exists()) return emptyList()
        return try {
            dir.listFiles { file -> file.extension == "json" }?.mapNotNull { file ->
                try {
                    val json = JSONObject(file.readText())
                    CustomBrushData.fromJson(json)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse custom brush failed: ${file.name}", e)
                    null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Load custom brushes failed", e)
            emptyList()
        }
    }

    fun deleteCustomBrushConfig(id: String): Boolean {
        val file = File(CONFIG_DIR, "$id.json")
        return if (file.exists()) file.delete() else true
    }
}