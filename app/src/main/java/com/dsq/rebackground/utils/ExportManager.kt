package com.dsq.rebackground.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import com.dsq.rebackground.model.LayerData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 导出管理器 - 负责各种格式的导出
 * 所有导出文件统一保存到 /storage/emulated/0/ReMoveBg-Config/exports/
 * 支持格式：PNG, JPEG, WebP, 草稿(.rebrush), PSD(简化版)
 */
object ExportManager {
    private const val TAG = "ExportManager"

    // 导出根目录（与 MainActivity 中的初始化目录一致）
    private const val BASE_CONFIG_DIR = "/storage/emulated/0/ReMoveBg-Config"
    private const val EXPORT_BASE_DIR = "$BASE_CONFIG_DIR/exports"

    // 导出格式枚举
    enum class ExportFormat(val extension: String, val mimeType: String) {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp"),
        DRAFT("rebrush", "application/octet-stream"),
        PSD("psd", "image/vnd.adobe.photoshop");

        companion object {
            fun fromIndex(index: Int): ExportFormat {
                return when (index) {
                    0 -> PNG
                    1 -> JPEG
                    2 -> WEBP
                    3 -> DRAFT
                    4 -> PSD
                    else -> PNG
                }
            }
        }
    }

    // ========== 导出主入口 ==========

    fun export(
        bitmap: Bitmap,
        fileName: String,
        format: ExportFormat,
        context: Context,
        layers: List<LayerData>? = null,
        backgroundColor: Int = Color.WHITE
    ): Boolean {
        return try {
            when (format) {
                ExportFormat.PNG -> exportImage(bitmap, fileName, format, context, CompressFormat.PNG, 100)
                ExportFormat.JPEG -> exportImage(bitmap, fileName, format, context, CompressFormat.JPEG, 90)
                ExportFormat.WEBP -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        exportImage(bitmap, fileName, format, context, CompressFormat.WEBP, 90)
                    } else {
                        ToastUtil.showToast(context, "WebP 仅支持 Android 11+，已降级为 PNG")
                        exportImage(bitmap, fileName, ExportFormat.PNG, context, CompressFormat.PNG, 100)
                    }
                }
                ExportFormat.DRAFT -> exportDraft(bitmap, fileName, context, backgroundColor)
                ExportFormat.PSD -> exportPSD(bitmap, fileName, context, layers)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            ToastUtil.showErrorToast(context, "导出失败: ${e.message}")
            false
        }
    }

    // ========== 统一的文件保存（所有格式） ==========

    private fun saveFile(context: Context, data: ByteArray, fileName: String) {
        val dir = File(EXPORT_BASE_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("无法创建导出目录: ${dir.absolutePath}")
        }
        val file = File(dir, fileName)
        FileOutputStream(file).use { fos ->
            fos.write(data)
            fos.flush()
        }
        ToastUtil.showToast(context, "已保存到: ${file.absolutePath}")
        Log.d(TAG, "Saved to: ${file.absolutePath}")
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String, format: CompressFormat, quality: Int) {
        val dir = File(EXPORT_BASE_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("无法创建导出目录: ${dir.absolutePath}")
        }
        val file = File(dir, fileName)
        FileOutputStream(file).use { fos ->
            bitmap.compress(format, quality, fos)
            fos.flush()
        }
        ToastUtil.showToast(context, "已保存到: ${file.absolutePath}")
        Log.d(TAG, "Saved to: ${file.absolutePath}")
    }

    // ========== 图片格式导出 ==========

    private fun exportImage(
        bitmap: Bitmap,
        fileName: String,
        format: ExportFormat,
        context: Context,
        compressFormat: CompressFormat,
        quality: Int
    ) {
        val finalName = "$fileName.${format.extension}"
        saveBitmap(context, bitmap, finalName, compressFormat, quality)
    }

    // ========== 草稿格式 (.rebrush) ==========

    private fun exportDraft(bitmap: Bitmap, fileName: String, context: Context, backgroundColor: Int): Boolean {
        return try {
            val json = JSONObject().apply {
                put("version", 1)
                put("createdAt", System.currentTimeMillis())
                put("fileName", fileName)
                put("canvasWidth", bitmap.width)
                put("canvasHeight", bitmap.height)
                put("backgroundColor", backgroundColor)

                // 缩略图
                val thumbBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                val thumbBytes = java.io.ByteArrayOutputStream()
                thumbBitmap.compress(CompressFormat.PNG, 80, thumbBytes)
                thumbBitmap.recycle()
                put("thumbnail", android.util.Base64.encodeToString(thumbBytes.toByteArray(), android.util.Base64.DEFAULT))

                // 图层数据（目前单层）
                val layersArray = JSONArray().apply {
                    val layerObj = JSONObject().apply {
                        put("name", "背景")
                        put("opacity", 1.0)
                        put("visible", true)
                        val bytes = java.io.ByteArrayOutputStream()
                        bitmap.compress(CompressFormat.PNG, 100, bytes)
                        put("imageData", android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.DEFAULT))
                    }
                    put(layerObj)
                }
                put("layers", layersArray)
            }

            val finalName = "$fileName.rebrush"
            saveFile(context, json.toString().toByteArray(Charsets.UTF_8), finalName)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Draft export failed", e)
            false
        }
    }

    /**
     * 从文件加载草稿（返回 DraftData）
     */
    fun loadDraft(context: Context, file: File): DraftData? {
        return try {
            val content = file.readText(Charsets.UTF_8)
            val json = JSONObject(content)
            val backgroundColor = json.optInt("backgroundColor", Color.WHITE)
            val layersArray = json.getJSONArray("layers")
            val firstLayer = layersArray.getJSONObject(0)
            val imageData = firstLayer.getString("imageData")
            val bytes = android.util.Base64.decode(imageData, android.util.Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) DraftData(bitmap, backgroundColor) else null
        } catch (e: Exception) {
            Log.e(TAG, "Load draft failed", e)
            null
        }
    }

    /**
     * 从 Uri 加载草稿（返回 DraftData）
     */
    fun loadDraftFromUri(context: Context, uri: Uri): DraftData? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val content = inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(content)
            val backgroundColor = json.optInt("backgroundColor", Color.WHITE)
            val layersArray = json.getJSONArray("layers")
            val firstLayer = layersArray.getJSONObject(0)
            val imageData = firstLayer.getString("imageData")
            val bytes = android.util.Base64.decode(imageData, android.util.Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) DraftData(bitmap, backgroundColor) else null
        } catch (e: Exception) {
            Log.e(TAG, "Load draft from Uri failed", e)
            null
        }
    }

    // ========== PSD 导出（简化版） ==========

    private fun exportPSD(bitmap: Bitmap, fileName: String, context: Context, layers: List<LayerData>?): Boolean {
        return try {
            // 保存主图片为 PNG（实际扩展名为 .psd）
            val imageName = "$fileName.psd"
            saveBitmap(context, bitmap, imageName, CompressFormat.PNG, 100)

            // 保存图层描述 JSON
            val jsonName = "${fileName}_layers.json"
            val json = JSONObject().apply {
                put("version", 1)
                put("layers", layers?.map {
                    JSONObject().apply {
                        put("name", it.name)
                        put("opacity", it.opacity)
                        put("visible", it.visible)
                        put("width", it.width)
                        put("height", it.height)
                    }
                } ?: JSONArray())
            }
            saveFile(context, json.toString().toByteArray(Charsets.UTF_8), jsonName)

            ToastUtil.showToast(context, "PSD 已保存（含图层描述）")
            true
        } catch (e: Exception) {
            Log.e(TAG, "PSD export failed", e)
            false
        }
    }

    // ========== 工具方法 ==========

    fun getExportDirectory(): File = File(EXPORT_BASE_DIR)

    fun listExportedFiles(): List<File> {
        val dir = File(EXPORT_BASE_DIR)
        return if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { it.isFile } ?: emptyList()
        } else {
            emptyList()
        }
    }
}

// 草稿数据类（仅用于内部，放在文件末尾）
data class DraftData(val bitmap: Bitmap, val backgroundColor: Int)