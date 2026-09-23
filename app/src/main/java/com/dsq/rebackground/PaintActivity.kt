
//有日志=======
package com.dsq.rebackground

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

import com.dsq.rebackground.model.LayerData
import com.dsq.rebackground.paint.brush.BrushDefinition
import com.dsq.rebackground.paint.brush.BrushDynamics
import com.dsq.rebackground.paint.brush.BrushTip
import com.dsq.rebackground.paint.rendering.gl.PaintGLSurfaceView
import com.dsq.rebackground.paint.ui.PaintEngineController
import com.dsq.rebackground.utils.BrushTextureHelper
import com.dsq.rebackground.utils.DraftData
import com.dsq.rebackground.utils.ExportManager
import com.dsq.rebackground.utils.ToastUtil
import com.dsq.rebackground.paint.brush.BrushItem
import com.dsq.rebackground.paint.brush.BrushLibrary
import com.dsq.rebackground.paint.brush.BrushLoader
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class PaintActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_COLOR = 1001
        private const val REQUEST_SETTINGS = 1004
        private const val REQUEST_BRUSH_LIBRARY = 1005
        private const val REQUEST_IMPORT_DRAFT = 1006
        private const val TAG = "PaintActivity"
        private const val COLOR_TAG = "COLOR_TRACE"
        private const val MAX_BRUSH_PIXEL = 80
    }


    private lateinit var paintGlSurface: PaintGLSurfaceView
    private lateinit var engineController: PaintEngineController

    // 顶部工具栏控件
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnResetTransform: ImageButton
    private lateinit var btnLayers: ImageButton
    private lateinit var btnSettings: ImageButton

    // 底部工具栏控件
    private lateinit var brushSelector: LinearLayout
    private lateinit var currentBrushIcon: ImageView
    private lateinit var brushLabel: TextView
    private lateinit var currentBrushName: TextView
    private lateinit var btnColor: ImageButton
    private lateinit var brushSizeSeekBar: SeekBar
    private lateinit var brushSizeText: TextView
    private lateinit var btnEraser: ImageButton
    private lateinit var btnClear: ImageButton

    // 画笔数据
    private var brushItems: MutableList<BrushItem> = mutableListOf()
    private var currentBrushItem: BrushItem? = null

    // 画笔状态
    private var currentColor = Color.BLACK
    private var isEraser = false
    private var currentSize = 0.1f
    // [MOD PR-2.7] 初值 1.0（会被 loadAllBrushes 覆盖为笔刷 defaultFlow）
    private var currentFlow = 1.0f
    private var currentOpacity = 0.99f
    private var currentRotation = true
    // [MOD PR-2.3] 待应用的文档尺寸（0 = 用屏幕尺寸兜底）
    private var pendingDocW = 0
    private var pendingDocH = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paint)

        Log.d(TAG, "=== onCreate ===")

        initPaintEngine()
        engineController.setPaper(intent.getStringExtra("paperId") ?: "medium")
        bindViews()
        loadAllBrushes()
        loadBrushTextures()
        setupBrushSelector()
        setupListeners()
        setupUndoRedo()
        applyBrushSettings()
        updateColorButton()
        updateSizeText()
        updateBrushLabelColor()

        // 检查是否有草稿 Uri 传入（从创建画布界面）
        val draftUriStr = intent.getStringExtra("draftUri")
        if (!draftUriStr.isNullOrEmpty()) {
            try {
                val draftUri = Uri.parse(draftUriStr)

            } catch (e: Exception) {
                Log.e(TAG, "解析草稿 Uri 失败", e)
                ToastUtil.showErrorToast(this, "草稿文件无效")
            }
        }
        // [MOD PR-2.1] 处理"用图片创建画布"
        // ============================================================
        // [MOD PR-2.3] 从 intent 读取画布尺寸 / 图片
        //   - imageUri 存在：先用屏幕尺寸 attach，图片解码后再 setDocumentSize
        //   - canvasWidth/Height 存在：直接作为 document 尺寸
        //   - 都无：document = 屏幕尺寸（旧行为）
        // ============================================================
        val imageUriStr = intent.getStringExtra("imageUri")
        val intentCanvasW = intent.getIntExtra("canvasWidth", 0)
        val intentCanvasH = intent.getIntExtra("canvasHeight", 0)

        if (!imageUriStr.isNullOrEmpty()) {
            Log.d(TAG, "Image-mode: will decode background and re-attach")
            paintGlSurface.post {
                decodeAndSetBackgroundImage(imageUriStr)
            }
        } else if (intentCanvasW > 0 && intentCanvasH > 0) {
            pendingDocW = intentCanvasW
            pendingDocH = intentCanvasH
            Log.d(TAG, "Canvas size from intent: ${intentCanvasW}x$intentCanvasH")
        } else {
            Log.d(TAG, "No canvas size specified, using screen size")
        }


        Log.d(TAG, "=== onCreate done ===")
    }

    // ============================================================
    // [MOD PR-2.3] 解码图片并作为画布背景
    //
    // 步骤：
    //   1. 后台解码图片（inSampleSize 防 OOM）
    //   2. 主线程：设置 document 尺寸 = 图片尺寸 → 重建 FBO
    //   3. 把图片作为 canvas 底层内容绘制
    // ============================================================
    private fun decodeAndSetBackgroundImage(uriStr: String) {
        Thread {
            try {
                val uri = Uri.parse(uriStr)

                // 1. 读尺寸
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    runOnUiThread { ToastUtil.showErrorToast(this, "读取图片失败") }
                    return@Thread
                }

                // 2. 计算 inSampleSize（最大边 ≤ 2048）
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                var sampleSize = 1
                while (maxSide / sampleSize > 2048) sampleSize *= 2

                // 3. 解码
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
                if (bitmap == null) {
                    runOnUiThread { ToastUtil.showErrorToast(this, "读取图片失败") }
                    return@Thread
                }

                // 4. 主线程应用
                runOnUiThread {
                    val w = bitmap.width
                    val h = bitmap.height
                    Log.d(TAG, "Image decoded: ${w}x$h, re-attaching with doc size")

                    // 用图片尺寸作为 document 尺寸
                    pendingDocW = w
                    pendingDocH = h
                    engineController.setDocumentSize(w, h)

                    // 延迟一帧再设背景图，确保 attach/FBO 重建完成
                    paintGlSurface.post {
                        paintGlSurface.setBackgroundBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "decodeAndSetBackgroundImage failed", e)
                runOnUiThread { ToastUtil.showErrorToast(this, "读取图片失败") }
            }
        }.start()
    }
    // ============================================================

    // [MOD PR-2.3] 防止重复 attach
    private var lastAttachedScreenW = -1
    private var lastAttachedScreenH = -1
    private var lastAttachedDocW = -1
    private var lastAttachedDocH = -1

    private fun initPaintEngine() {
        paintGlSurface = findViewById(R.id.paintGlSurface)
        paintGlSurface.visibility = View.VISIBLE
        engineController = PaintEngineController(paintGlSurface)
        paintGlSurface.setOnTouchListener { _, event ->
            Log.d(TAG, "Touch event: action=${event.actionMasked}, pointers=${event.pointerCount}")
            engineController.onMotionEvent(event)
            updateUndoRedoState()
            true
        }
        paintGlSurface.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val w = right - left
            val h = bottom - top
            if (w <= 0 || h <= 0) return@addOnLayoutChangeListener

            // [MOD PR-2.3] 优先用 intent 尺寸；否则用屏幕尺寸
            val docW = if (pendingDocW > 0) pendingDocW else w
            val docH = if (pendingDocH > 0) pendingDocH else h

            if (w == lastAttachedScreenW && h == lastAttachedScreenH &&
                docW == lastAttachedDocW && docH == lastAttachedDocH) return@addOnLayoutChangeListener

            lastAttachedScreenW = w
            lastAttachedScreenH = h
            lastAttachedDocW = docW
            lastAttachedDocH = docH

            Log.d(TAG, "attach: screen=${w}x$h doc=${docW}x$docH")
            engineController.attach(w, h, docW, docH)
        }
        Log.d(TAG, "Paint engine initialized")
    }

    override fun onResume() {
        super.onResume()
        paintGlSurface.onResume()
    }

    override fun onPause() {
        paintGlSurface.onPause()
        super.onPause()
    }

    private fun bindViews() {
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnResetTransform = findViewById(R.id.btnResetTransform)
        btnLayers = findViewById(R.id.btnLayers)
        btnSettings = findViewById(R.id.btnSettings)

        brushSelector = findViewById(R.id.brushSelector)
        currentBrushIcon = findViewById(R.id.currentBrushIcon)
        brushLabel = findViewById(R.id.brushLabel)
        currentBrushName = findViewById(R.id.currentBrushName)
        btnColor = findViewById(R.id.btnColor)
        brushSizeSeekBar = findViewById(R.id.brushSizeSeekBar)
        brushSizeText = findViewById(R.id.brushSizeText)
        btnEraser = findViewById(R.id.btnEraser)
        btnClear = findViewById(R.id.btnClear)

        Log.d(TAG, "Views bound")
    }
    private fun loadAllBrushes() {
        brushItems = BrushLibrary.allBrushes(this).toMutableList()

        currentBrushItem = brushItems.find { it.id == "preset_electric" }
            ?: brushItems.firstOrNull()

        // ============================================================
        // [MOD PR-2.7] 同步 flow/opacity 到 UI 状态
        //   漏了这一步 → currentFlow 保持 0.5 → 浓度永远 0.5
        // ============================================================
        currentBrushItem?.let { item ->
            currentFlow = item.defaultFlow
            currentOpacity = item.defaultOpacity
        }
        // ============================================================

        Log.d(TAG, "loadAllBrushes: total=${brushItems.size}, current=${currentBrushItem?.id} " +
                "flow=$currentFlow opacity=$currentOpacity")
    }
    private fun setupBrushSelector() {
        brushSelector.setOnClickListener {
            val intent = Intent(this, BrushLibraryActivity::class.java)
            startActivityForResult(intent, REQUEST_BRUSH_LIBRARY)
        }
        updateCurrentBrushDisplay()
    }
    private fun updateCurrentBrushDisplay() {
        val item = currentBrushItem
        if (item == null) {
            currentBrushName.text = "未选择"
            currentBrushIcon.setImageResource(R.drawable.ic_brush_default)
            brushLabel.setTextColor(Color.WHITE)
            Log.w(TAG, "No brush selected")
            return
        }

        currentBrushName.text = item.displayName

        // [MOD PR-2 Step 2] 用 texturePath 判断，不再依赖 config.stamp
        when {
            item.thumbnailPath != null -> {
                BitmapFactory.decodeFile(item.thumbnailPath)?.let { bitmap ->
                    currentBrushIcon.setImageBitmap(bitmap)
                } ?: run {
                    currentBrushIcon.setImageResource(R.drawable.ic_brush_default)
                }
            }
            item.id == "preset_electric" -> {
                // 电子画笔：程序化圆，无纹理
                currentBrushIcon.setImageResource(R.drawable.ic_brush_default)
            }
            item.texturePath != null -> {
                val bitmap = BrushLoader.loadTextureFromPath(this, item.texturePath)
                if (bitmap != null) {
                    val thumb = BrushTextureHelper.generateThumbnail(bitmap)
                    currentBrushIcon.setImageBitmap(thumb)
                } else {
                    currentBrushIcon.setImageResource(R.drawable.ic_brush_default)
                }
            }
            else -> {
                currentBrushIcon.setImageResource(R.drawable.ic_brush_default)
            }
        }

        brushLabel.setTextColor(currentColor)
        Log.d(TAG, "Update UI brush: ${item.displayName}, id=${item.id}")
    }
    private fun updateBrushLabelColor() {
        brushLabel.setTextColor(currentColor)
    }

    // ==================== 撤销/重做 ====================
    private fun setupUndoRedo() {
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        val canUndo = engineController.canUndo
        val canRedo = engineController.canRedo
        btnUndo.isEnabled = canUndo
        btnUndo.alpha = if (canUndo) 1.0f else 0.4f
        btnRedo.isEnabled = canRedo
        btnRedo.alpha = if (canRedo) 1.0f else 0.4f
        Log.d(TAG, "updateUndoRedoState: undo=$canUndo, redo=$canRedo")
    }

    private fun setupListeners() {
        btnUndo.setOnClickListener {
            Log.d(TAG, "Undo clicked")
            engineController.undo()
            updateUndoRedoState()
        }
        btnRedo.setOnClickListener {
            Log.d(TAG, "Redo clicked")
            engineController.redo()
            updateUndoRedoState()
        }

        btnResetTransform.setOnClickListener {
            Log.d(TAG, "Reset transformation clicked")
            engineController.resetViewTransform()
        }
        btnLayers.setOnClickListener {
            Log.d(TAG, "Layers clicked")
            showLayerDialog()
        }
        btnSettings.setOnClickListener {
            Log.d(TAG, "Settings clicked")
            openSettings()
        }

        btnColor.setOnClickListener {
            Log.d(TAG, "Color button clicked")
            startColorPicker()
        }
        btnEraser.setOnClickListener {
            Log.d(TAG, "Eraser button clicked")
            toggleEraser()
        }
        btnClear.setOnClickListener {
            Log.d(TAG, "Clear button clicked")
            engineController.clear()
            updateUndoRedoState()
        }

        brushSizeSeekBar.max = MAX_BRUSH_PIXEL - 1
        brushSizeSeekBar.progress = 9
        brushSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val pixel = progress + 1
                    currentSize = pixel.toFloat() / MAX_BRUSH_PIXEL
                    updateSizeText()
                    Log.d(TAG, "Size changed: pixel=$pixel, size=$currentSize")
                    applyBrushSettings()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        Log.d(TAG, "UI listeners setup complete")
    }

    private fun applyBrushSettings() {
        try {
            val diameter = (currentSize * MAX_BRUSH_PIXEL).coerceAtLeast(1f)
            val item = currentBrushItem

            // [MOD PR-2 Step 2] 删除翻译层，直接用 BrushItem 的字段
            val brush = if (item != null) {
                BrushDefinition(
                    id = item.id,
                    displayName = item.displayName,
                    baseDiameterDocumentUnits = diameter,
                    tip = item.tip,
                    material = item.material,
                    dynamics = item.defaultDynamics,
                    opacity = if (isEraser) 1f else currentOpacity,
                    flow = currentFlow
                )
            } else {
                BrushDefinition(
                    id = "default-round",
                    displayName = "默认圆形",
                    baseDiameterDocumentUnits = diameter,
                    opacity = if (isEraser) 1f else currentOpacity,
                    flow = currentFlow
                )
            }
            engineController.setBrush(brush)

            // ======================= COLOR_TRACE 日志 =======================
            Log.d(
                COLOR_TAG,
                "applyBrushSettings: 即将 setColor, currentColor=#${Integer.toHexString(currentColor)} " +
                        "isEraser=$isEraser"
            )
            // ================================================================

            val color = if (isEraser) {
                Triple(1f, 1f, 1f)
            } else {
                Triple(
                    Color.red(currentColor) / 255f,
                    Color.green(currentColor) / 255f,
                    Color.blue(currentColor) / 255f
                )
            }

            // ======================= COLOR_TRACE 日志 =======================
            Log.d(
                COLOR_TAG,
                "applyBrushSettings: 即将调用 engineController.setColor(${color.first}, ${color.second}, ${color.third})"
            )
            // ================================================================

            engineController.setColor(color.first, color.second, color.third)

            // ======================= COLOR_TRACE 日志 =======================
            Log.d(COLOR_TAG, "applyBrushSettings: engineController.setColor 调用完成")
            // ================================================================

            Log.d(TAG, "✅ New brush applied: name=${brush.displayName}, size=$currentSize, isEraser=$isEraser")
        } catch (e: Exception) {
            Log.e(TAG, "❌ applyBrushSettings error", e)
        }
    }
    private fun loadBrushTextures() {
        // [MOD PR-2 Step 2] 用 BrushLoader 批量加载，key = texturePath
        val textures = BrushLoader.loadAllTextures(this, brushItems)
        paintGlSurface.setBrushTextures(textures)
        Log.d(TAG, "loadBrushTextures: ${textures.size} textures loaded")
    }

    private fun showLayerDialog() {
        val layers = engineController.layers()
        val names = layers.map {
            if (it.id == engineController.activeLayer().id) "● ${it.name}" else "○ ${it.name}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("图层")
            .setItems(names) { _, which ->
                engineController.selectLayer(layers[which].id)
            }
            .setPositiveButton("新建图层") { _, _ ->
                engineController.addLayer("图层 ${engineController.layers().size + 1}")
                ToastUtil.showToast(this, "已新建图层")
            }
            .setNegativeButton("删除当前图层") { _, _ ->
                if (engineController.layers().size > 1) {
                    engineController.removeLayer(engineController.activeLayer().id)
                } else {
                    ToastUtil.showToast(this, "至少保留一个图层")
                }
            }
            .setNeutralButton("显示/隐藏当前图层") { _, _ ->
                engineController.toggleLayerVisibility(engineController.activeLayer().id)
            }
            .show()
    }

    private fun toggleEraser() {
        isEraser = !isEraser
        if (isEraser) {
            btnEraser.setBackgroundColor(Color.parseColor("#33FF9800"))
            btnEraser.setImageResource(R.drawable.ic_eraser_active)
            Log.d(TAG, "Eraser mode ON")
        } else {
            btnEraser.setBackgroundColor(Color.TRANSPARENT)
            btnEraser.setImageResource(R.drawable.ic_eraser)
            Log.d(TAG, "Eraser mode OFF")
        }
        applyBrushSettings()
    }

    private fun updateSizeText() {
        val pixel = (currentSize * MAX_BRUSH_PIXEL).roundToInt()
        brushSizeText.text = pixel.toString()
        brushSizeSeekBar.progress = pixel - 1
        Log.d(TAG, "Size text updated: $pixel px")
    }

    private fun updateColorButton() {
        val drawable = btnColor.background as? GradientDrawable
        drawable?.setColor(currentColor)
        btnColor.invalidate()
        updateBrushLabelColor()

        // ======================= COLOR_TRACE 日志 =======================
        Log.d(
            COLOR_TAG,
            "updateColorButton: currentColor=#${Integer.toHexString(currentColor)} " +
                    "R=${Color.red(currentColor)} G=${Color.green(currentColor)} B=${Color.blue(currentColor)} " +
                    "A=${Color.alpha(currentColor)}"
        )
        // ================================================================

        Log.d(TAG, "Color button updated: ${Integer.toHexString(currentColor)}")
    }

    private fun startColorPicker() {
        val intent = Intent(this, color_picker_view::class.java)
        startActivityForResult(intent, REQUEST_COLOR)
    }

    private fun openSettings() {
        val intent = Intent(this, PaintSettingsActivity::class.java).apply {
            putExtra(PaintSettingsActivity.EXTRA_FLOW, currentFlow)
            putExtra(PaintSettingsActivity.EXTRA_OPACITY, currentOpacity)
            putExtra(PaintSettingsActivity.EXTRA_ROTATION, currentRotation)
            putExtra(PaintSettingsActivity.EXTRA_TEXTURE_INDEX, 0)
        }
        startActivityForResult(intent, REQUEST_SETTINGS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_COLOR -> {
                if (resultCode == RESULT_OK) {
                    val hex = data?.getStringExtra("bgColor")

                    // ======================= COLOR_TRACE 日志 =======================
                    Log.d(COLOR_TAG, "onActivityResult REQUEST_COLOR: 收到原始 hex = $hex, resultCode=$resultCode")
                    // ================================================================

                    hex?.let {
                        try {
                            currentColor = Color.parseColor(it)

                            // ======================= COLOR_TRACE 日志 =======================
                            Log.d(
                                COLOR_TAG,
                                "onActivityResult: Color.parseColor 成功, currentColor=#${Integer.toHexString(currentColor)} " +
                                        "R=${Color.red(currentColor)} G=${Color.green(currentColor)} B=${Color.blue(currentColor)} " +
                                        "A=${Color.alpha(currentColor)}"
                            )
                            // ================================================================

                            if (isEraser) {
                                toggleEraser()
                            } else {
                                applyBrushSettings()
                            }
                            updateColorButton()
                            Log.d(TAG, "Color changed to: $it")
                        } catch (e: Exception) {
                            Log.e(COLOR_TAG, "Color.parseColor 失败: hex=$it", e)
                            Log.e(TAG, "Color parse error", e)
                        }
                    }
                } else {
                    Log.d(COLOR_TAG, "onActivityResult REQUEST_COLOR: resultCode=$resultCode (非 RESULT_OK)")
                }
            }
            REQUEST_SETTINGS -> {
                if (resultCode == RESULT_OK) {
                    data?.let { intentData ->
                        val exportFilename = intentData.getStringExtra(PaintSettingsActivity.EXTRA_EXPORT_FILENAME)
                        val exportFormat = intentData.getIntExtra(PaintSettingsActivity.EXTRA_EXPORT_FORMAT, 0)

                        if (!exportFilename.isNullOrEmpty()) {
                            Log.d(TAG, "Export requested: filename=$exportFilename, format=$exportFormat")
                            exportImageWithParams(exportFilename, exportFormat)
                        }

                        val draftUri = intentData.data
                        if (draftUri != null) {
                            Log.d(TAG, "Draft import from settings: $draftUri")
                            importDraftWithConfirm(draftUri)
                        }

                        currentFlow = intentData.getFloatExtra(PaintSettingsActivity.EXTRA_FLOW, 0.5f)
                        currentOpacity = intentData.getFloatExtra(PaintSettingsActivity.EXTRA_OPACITY, 0.99f)
                        currentRotation = intentData.getBooleanExtra(PaintSettingsActivity.EXTRA_ROTATION, true)

                        applyBrushSettings()
                        Log.d(TAG, "Settings applied: flow=$currentFlow, opacity=$currentOpacity, rotation=$currentRotation")
                    }
                }
            }
            REQUEST_BRUSH_LIBRARY -> {
                if (resultCode == RESULT_OK) {
                    val brushId = data?.getStringExtra(BrushLibraryActivity.EXTRA_SELECTED_BRUSH_ID)
                    if (brushId != null) {
                        loadAllBrushes()
                        loadBrushTextures()
                        val selected = brushItems.find { it.id == brushId }
                        if (selected != null) {
                            currentBrushItem = selected
                            // ============================================================
                            // [MOD PR-2.7] 切换笔刷时同步 flow/opacity
                            // ============================================================
                            currentFlow = selected.defaultFlow
                            currentOpacity = selected.defaultOpacity
                            // ============================================================
                            updateCurrentBrushDisplay()
                            applyBrushSettings()
                            Log.d(TAG, "Brush selected: ${selected.displayName} " +
                                    "flow=$currentFlow opacity=$currentOpacity")
                        } else {
                            Log.w(TAG, "Brush not found: $brushId")
                        }
                    }
                }
            }
            REQUEST_IMPORT_DRAFT -> {
                if (resultCode == RESULT_OK) {
                    val draftUri = data?.data
                    if (draftUri != null) {
                        importDraftWithConfirm(draftUri)
                    } else {
                        ToastUtil.showErrorToast(this, "未选择草稿文件")
                    }
                }
            }
        }
    }

    // ==================== 导出功能 ====================

    private fun exportImage() {
        val defaultName = "ReBackground_${System.currentTimeMillis()}"
        exportImageWithParams(defaultName, 0)
    }

    private fun exportImageWithParams(fileName: String, formatIndex: Int) {
        paintGlSurface.captureBitmap { bitmap ->
            Log.d(TAG, "Export capture callback, bitmap=${bitmap != null}, formatIndex=$formatIndex")
            if (bitmap == null) {
                ToastUtil.showToast(this, "画布未初始化")
                return@captureBitmap
            }

            val format = ExportManager.ExportFormat.fromIndex(formatIndex)
            val layerData = engineController.layers().map { layer ->
                LayerData(
                    name = layer.name,
                    bitmap = bitmap,
                    opacity = 1f,
                    visible = layer.visible,
                    width = bitmap.width,
                    height = bitmap.height
                )
            }
            ExportManager.export(
                bitmap = bitmap,
                fileName = fileName,
                format = format,
                context = this,
                layers = layerData,
                backgroundColor = Color.WHITE
            )
        }
    }

    // ==================== 草稿导入（带确认） ====================

    private fun importDraftWithConfirm(draftUri: Uri) {
        performImportDraft(draftUri)
    }

    private fun performImportDraft(draftUri: Uri) {
        // [MOD 2026-09-19 PR-1] 草稿导入功能暂时禁用（PR-3 会重写）
        Log.w(TAG, "Draft import temporarily disabled (pending PR-3)")
        ToastUtil.showToast(this, "草稿导入功能暂不可用")
    }
}
