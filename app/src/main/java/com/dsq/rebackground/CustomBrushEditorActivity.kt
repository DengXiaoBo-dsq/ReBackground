package com.dsq.rebackground

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dsq.rebackground.paint.brush.BrushDefinition
import com.dsq.rebackground.paint.brush.BrushItem
import com.dsq.rebackground.paint.brush.BrushLibrary
import com.dsq.rebackground.paint.brush.BrushLoader
import com.dsq.rebackground.paint.brush.CustomBrushData
import com.dsq.rebackground.paint.rendering.gl.PaintGLSurfaceView
import com.dsq.rebackground.paint.ui.PaintEngineController
import com.dsq.rebackground.utils.BrushTextureHelper
import com.dsq.rebackground.utils.ToastUtil
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * 自定义画笔纹理编辑器（PR-2 Step 3 全面重写）
 *
 * 关键变化：
 *   - 画布： → PaintGLSurfaceView + PaintEngineController
 *   - 画笔：使用 paint/brush/BrushItem，不再依赖  BrushConfig
 *   - 颜色：固定黑色（0,0,0），在白底上绘制
 *   - 导出：captureBitmap → resize 到目标尺寸 → 亮度转 alpha → 得到标准笔刷 PNG
 *
 * 导出 PNG 语义：
 *   - 白底 → alpha=0（透明）
 *   - 黑色笔迹 → alpha=255，RGB=白色（不透明）
 *   - 中间灰阶 → 半透明白
 *   这是笔刷纹理的标准格式（形状由 alpha 通道表达）
 */
class CustomBrushEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BRUSH_ID = "extra_brush_id"
        const val RESULT_BRUSH_SAVED = 1
        const val EXTRA_SAVED_BRUSH_ID = "extra_saved_brush_id"
        private const val TAG = "CustomBrushEditor"
        private const val DEFAULT_SIZE = 512
        private const val MIN_SIZE = 16
        private const val MAX_SIZE = 2048
    }

    // 画布
    private lateinit var canvasFrame: FrameLayout
    private lateinit var paintGlSurface: PaintGLSurfaceView
    private lateinit var engineController: PaintEngineController

    // 工具栏
    private lateinit var brushSelector: LinearLayout
    private lateinit var currentBrushIcon: ImageView
    private lateinit var currentBrushName: TextView

    // 底部工具条
    private lateinit var sbSize: SeekBar
    private lateinit var tvSizeValue: TextView
    private lateinit var sbOpacity: SeekBar
    private lateinit var tvOpacityValue: TextView
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    // 尺寸选择
    private lateinit var sizeSpinner: Spinner
    private lateinit var customSizeLayout: LinearLayout
    private lateinit var customWidthInput: EditText
    private lateinit var customHeightInput: EditText
    private lateinit var btnApplyCustomSize: Button

    // 状态
    private var brushItems: List<BrushItem> = emptyList()
    private var currentBrushItem: BrushItem? = null
    private var currentSizePixels = 10f          // 1~80
    private var currentOpacity = 0.99f
    private var editingBrushId: String? = null

    // 导出目标尺寸（与 GL 画布尺寸解耦）
    private var targetExportWidth = DEFAULT_SIZE
    private var targetExportHeight = DEFAULT_SIZE

    // 记录上一次 attach 的尺寸，避免重复 attach
    private var lastAttachedWidth = -1
    private var lastAttachedHeight = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_brush_editor)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editingBrushId = intent.getStringExtra(EXTRA_BRUSH_ID)

        // [PR-2 Step 3] 编辑已有笔刷暂不支持（D1 决策 B）
        if (editingBrushId != null) {
            ToastUtil.showToast(this, "编辑已有笔刷功能即将支持")
            finish()
            return
        }
        supportActionBar?.title = "绘制纹理"

        initViews()
        loadBrushes()
        loadBrushTextures()

        // 默认画笔：电子画笔（如果存在），否则第一个
        currentBrushItem = brushItems.find { it.id == "preset_electric" }
            ?: brushItems.firstOrNull()
        updateBrushDisplay()

        setupCanvasFrame()

        applyBrushSettings()
        setupListeners()
        updateSizeText()
        updateOpacityText()
        updateUndoRedoState()

        Log.d(TAG, "onCreate finished, currentBrushItem=${currentBrushItem?.displayName}")
    }

    override fun onResume() {
        super.onResume()
        paintGlSurface.onResume()
    }

    override fun onPause() {
        paintGlSurface.onPause()
        super.onPause()
    }

    // ========== 初始化视图 ==========

    private fun initViews() {
        canvasFrame = findViewById(R.id.canvasFrame)
        paintGlSurface = findViewById(R.id.paintGlSurface)
        paintGlSurface.visibility = View.VISIBLE
        engineController = PaintEngineController(paintGlSurface)

        brushSelector = findViewById(R.id.brushSelector)
        currentBrushIcon = findViewById(R.id.currentBrushIcon)
        currentBrushName = findViewById(R.id.currentBrushName)

        sbSize = findViewById(R.id.sbSize)
        tvSizeValue = findViewById(R.id.tvSizeValue)
        sbOpacity = findViewById(R.id.sbOpacity)
        tvOpacityValue = findViewById(R.id.tvOpacityValue)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnClear = findViewById(R.id.btnClear)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        sizeSpinner = findViewById(R.id.sizeSpinner)
        customSizeLayout = findViewById(R.id.customSizeLayout)
        customWidthInput = findViewById(R.id.customWidthInput)
        customHeightInput = findViewById(R.id.customHeightInput)
        btnApplyCustomSize = findViewById(R.id.btnApplyCustomSize)

        // 尺寸 Spinner
        val sizeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.texture_size_options,
            android.R.layout.simple_spinner_item
        )
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sizeSpinner.adapter = sizeAdapter
        sizeSpinner.setSelection(2) // 默认 512

        sbSize.progress = 9
        sbOpacity.progress = 99
    }

    // ========== 画布初始化 ==========

    /**
     * 让 PaintGLSurfaceView 变成正方形（取 canvasFrame 短边）。
     * 正方形是必须的：用户选 512x512 导出时，如果画布不是正方形，导出会变形。
     */
    private fun setupCanvasFrame() {
        // canvasFrame 每次 layout 后，把 paintGlSurface 调成正方形，然后 attach 引擎
        canvasFrame.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val size = min(r - l, b - t)
            if (size <= 0) return@addOnLayoutChangeListener

            val lp = paintGlSurface.layoutParams
            if (lp != null && (lp.width != size || lp.height != size)) {
                lp.width = size
                lp.height = size
                paintGlSurface.layoutParams = lp
            }
            // 布局变化可能滞后，用 post 保证在下一帧测量后 attach
            paintGlSurface.post { tryAttachEngine() }
        }

        // 保险：onCreate 结束后立即尝试一次
        paintGlSurface.post { tryAttachEngine() }
    }

    private fun tryAttachEngine() {
        val w = paintGlSurface.width
        val h = paintGlSurface.height
        if (w <= 0 || h <= 0) {
            Log.w(TAG, "tryAttachEngine skipped: w=$w h=$h")
            return
        }
        if (w == lastAttachedWidth && h == lastAttachedHeight) return

        lastAttachedWidth = w
        lastAttachedHeight = h
        // [MOD PR-2.3] attach 新签名：screen + doc
        // 编辑器是正方形画布，screen = doc = w × h
        engineController.attach(w, h, w, h)
        applyBrushSettings()  // attach 会重置引擎状态，必须重新设置画笔
        Log.d(TAG, "Canvas attached: screen=${w}x$h doc=${w}x$h")
    }

    // ========== 画笔加载 ==========

    private fun loadBrushes() {
        brushItems = BrushLibrary.allBrushes(this).sortedWith(compareBy(
            { if (it.group == "内置") 0 else 1 },
            { it.group },
            { it.displayName }
        ))
        Log.d(TAG, "loadBrushes: ${brushItems.size} brushes loaded")
    }

    private fun loadBrushTextures() {
        val textures = BrushLoader.loadAllTextures(this, brushItems)
        paintGlSurface.setBrushTextures(textures)
        Log.d(TAG, "loadBrushTextures: ${textures.size} textures loaded")
    }

    private fun updateBrushDisplay() {
        val item = currentBrushItem
        if (item == null) {
            currentBrushName.text = "未选择"
            currentBrushIcon.setImageResource(R.drawable.ic_brush_default)
            return
        }
        currentBrushName.text = item.displayName
        when {
            item.thumbnailPath != null -> {
                BitmapFactory.decodeFile(item.thumbnailPath)?.let {
                    currentBrushIcon.setImageBitmap(it)
                } ?: run {
                    currentBrushIcon.setImageResource(R.drawable.ic_brush_default)
                }
            }
            item.texturePath != null -> {
                val bitmap = BrushLoader.loadTextureFromPath(this, item.texturePath)
                if (bitmap != null) {
                    currentBrushIcon.setImageBitmap(BrushTextureHelper.generateThumbnail(bitmap))
                } else {
                    currentBrushIcon.setImageResource(R.drawable.ic_brush_default)
                }
            }
            else -> {
                currentBrushIcon.setImageResource(R.drawable.ic_brush_default)
            }
        }
    }

    // ========== 应用画笔到引擎 ==========

    private fun applyBrushSettings() {
        val item = currentBrushItem ?: return
        try {
            val brush = BrushDefinition(
                id = item.id,
                displayName = item.displayName,
                baseDiameterDocumentUnits = currentSizePixels,
                tip = item.tip,
                material = item.material,
                dynamics = item.defaultDynamics,
                opacity = currentOpacity,
                flow = item.defaultFlow
            )
            engineController.setBrush(brush)

            // 编辑器固定用黑色画笔（白底黑图 → 导出时转 alpha）
            engineController.setColor(0f, 0f, 0f)

            Log.d(TAG, "applyBrushSettings: brush=${item.displayName} size=$currentSizePixels opacity=$currentOpacity")
        } catch (e: Exception) {
            Log.e(TAG, "applyBrushSettings failed", e)
        }
    }

    // ========== 事件监听 ==========

    private fun setupListeners() {
        // 画布触摸
        paintGlSurface.setOnTouchListener { _, event ->
            engineController.onMotionEvent(event)
            updateUndoRedoState()
            true
        }

        // 画笔选择
        brushSelector.setOnClickListener { showBrushPickerDialog() }

        // 大小
        sbSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                currentSizePixels = (progress + 1).toFloat() // 1~80
                updateSizeText()
                applyBrushSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 透明度
        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                currentOpacity = (progress + 1) / 100f
                updateOpacityText()
                applyBrushSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 撤销 / 重做 / 清空
        btnUndo.setOnClickListener {
            engineController.undo()
            updateUndoRedoState()
        }
        btnRedo.setOnClickListener {
            engineController.redo()
            updateUndoRedoState()
        }
        btnClear.setOnClickListener {
            engineController.clear()
            updateUndoRedoState()
        }

        // 保存 / 取消
        btnSave.setOnClickListener { saveTextureAndBrush() }
        btnCancel.setOnClickListener { finish() }

        // 尺寸 Spinner（只改导出目标尺寸，不动画布）
        sizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> changeExportSize(128, 128)
                    1 -> changeExportSize(256, 256)
                    2 -> changeExportSize(512, 512)
                    3 -> customSizeLayout.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 自定义尺寸应用
        btnApplyCustomSize.setOnClickListener {
            val w = customWidthInput.text.toString().trim().toIntOrNull()
            val h = customHeightInput.text.toString().trim().toIntOrNull()
            if (w == null || h == null) {
                ToastUtil.showErrorToast(this, "请输入有效数字")
                return@setOnClickListener
            }
            if (w < MIN_SIZE || h < MIN_SIZE || w > MAX_SIZE || h > MAX_SIZE) {
                ToastUtil.showErrorToast(this, "尺寸范围 ${MIN_SIZE}~${MAX_SIZE}")
                return@setOnClickListener
            }
            changeExportSize(w, h)
            customSizeLayout.visibility = View.GONE
        }
    }

    private fun updateSizeText() {
        tvSizeValue.text = currentSizePixels.toInt().toString()
    }

    private fun updateOpacityText() {
        tvOpacityValue.text = String.format("%.2f", currentOpacity)
    }

    private fun updateUndoRedoState() {
        val canUndo = engineController.canUndo
        val canRedo = engineController.canRedo
        btnUndo.isEnabled = canUndo
        btnUndo.alpha = if (canUndo) 1.0f else 0.4f
        btnRedo.isEnabled = canRedo
        btnRedo.alpha = if (canRedo) 1.0f else 0.4f
    }

    /**
     * 改变导出目标尺寸。画布内容保留，不影响已画的内容。
     */
    private fun changeExportSize(w: Int, h: Int) {
        targetExportWidth = w
        targetExportHeight = h
        ToastUtil.showToast(this, "导出尺寸：${w}×${h}")
        Log.d(TAG, "changeExportSize: ${w}x$h (canvas unchanged)")
    }

    // ========== 画笔选择对话框 ==========

    private fun showBrushPickerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_brush_picker, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val dialog = AlertDialog.Builder(this)
            .setTitle("选择画笔")
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .create()

        val adapter = BrushPickerAdapter(brushItems) { selected ->
            currentBrushItem = selected
            updateBrushDisplay()
            applyBrushSettings()
            Log.d(TAG, "Brush selected: ${selected.displayName}, group=${selected.group}")
            dialog.dismiss()
        }
        recyclerView.adapter = adapter
        dialog.show()
    }

    private inner class BrushPickerAdapter(
        private val items: List<BrushItem>,
        private val onItemClick: (BrushItem) -> Unit
    ) : RecyclerView.Adapter<BrushPickerAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_brush_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], onItemClick)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView = itemView.findViewById<ImageView>(R.id.brush_icon)
            private val nameView = itemView.findViewById<TextView>(R.id.brush_name)
            private val groupView = itemView.findViewById<TextView>(R.id.brush_group)

            fun bind(brush: BrushItem, onClick: (BrushItem) -> Unit) {
                nameView.text = brush.displayName
                groupView.text = brush.group
                when {
                    brush.thumbnailPath != null -> {
                        BitmapFactory.decodeFile(brush.thumbnailPath)?.let {
                            iconView.setImageBitmap(it)
                        } ?: run {
                            iconView.setImageResource(R.drawable.ic_brush_default)
                        }
                    }
                    brush.texturePath != null -> {
                        val bitmap = BrushLoader.loadTextureFromPath(itemView.context, brush.texturePath)
                        if (bitmap != null) {
                            iconView.setImageBitmap(BrushTextureHelper.generateThumbnail(bitmap))
                        } else {
                            iconView.setImageResource(R.drawable.ic_brush_default)
                        }
                    }
                    else -> iconView.setImageResource(R.drawable.ic_brush_default)
                }
                itemView.setOnClickListener { onClick(brush) }
            }
        }
    }

    // ========== 保存流程 ==========

    private fun saveTextureAndBrush() {
        paintGlSurface.captureBitmap { bitmap ->
            if (bitmap == null) {
                ToastUtil.showErrorToast(this, "画布未初始化")
                return@captureBitmap
            }
            if (isCanvasEmpty(bitmap)) {
                ToastUtil.showErrorToast(this, "画布为空，请绘制纹理")
                return@captureBitmap
            }

            // 1. resize 到目标导出尺寸
            val scaled = if (bitmap.width == targetExportWidth && bitmap.height == targetExportHeight) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, targetExportWidth, targetExportHeight, true)
            }

            // 2. 亮度转 alpha（白底 → 透明，黑色笔迹 → 不透明白）
            val processed = convertLuminanceToAlpha(scaled)

            // 3. 弹保存对话框
            showSaveDialog(processed)
        }
    }

    /**
     * 白底黑图 → 标准笔刷纹理：
     *   luminance = 0.299R + 0.587G + 0.114B
     *   alpha = 255 - luminance
     *   RGB = 白色（0xFFFFFF）
     *
     * 效果：
     *   纯白 (255,255,255) → alpha=0   → 完全透明
     *   纯黑 (0,0,0)       → alpha=255 → 白色不透明
     *   灰色 (128,128,128) → alpha=127 → 半透明白
     */
    private fun convertLuminanceToAlpha(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)
            val alpha = 255 - luminance
            pixels[i] = (alpha shl 24) or 0xFFFFFF
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        return dst
    }

    /**
     * 判断画布是否为空（白底）。
     * 采样每 10 像素，如果有非白像素则认为有内容。
     */
    private fun isCanvasEmpty(bitmap: Bitmap): Boolean {
        val step = 10
        for (x in 0 until bitmap.width step step) {
            for (y in 0 until bitmap.height step step) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r < 240 || g < 240 || b < 240) return false
            }
        }
        return true
    }

    private fun showSaveDialog(processedBitmap: Bitmap) {
        val inputName = EditText(this).apply {
            hint = "画笔名称"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }

        val groups = BrushTextureHelper.getGroups(this).filter { it != "内置" }.toMutableList()
        if (groups.isEmpty()) groups.add("新分组（请在笔刷库中创建）")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@CustomBrushEditorActivity,
                android.R.layout.simple_spinner_item,
                groups
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        val sizeInfo = TextView(this).apply {
            text = "导出尺寸: ${processedBitmap.width}×${processedBitmap.height}"
            setTextColor(android.graphics.Color.GRAY)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 20)
            addView(TextView(this@CustomBrushEditorActivity).apply {
                text = "画笔名称"
                setTextColor(android.graphics.Color.WHITE)
            })
            addView(inputName)
            addView(TextView(this@CustomBrushEditorActivity).apply {
                text = "分组"
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 20 }
            })
            addView(spinner)
            addView(sizeInfo)
        }

        AlertDialog.Builder(this)
            .setTitle("保存画笔")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = inputName.text.toString().trim()
                if (name.isEmpty()) {
                    ToastUtil.showErrorToast(this, "请输入画笔名称")
                    return@setPositiveButton
                }
                if (!validateBrushName(name)) {
                    ToastUtil.showErrorToast(this, "名称包含非法字符")
                    return@setPositiveButton
                }
                val groupName = spinner.selectedItem as? String
                if (groupName.isNullOrEmpty() || groupName.startsWith("新分组")) {
                    ToastUtil.showErrorToast(this, "请选择有效分组")
                    return@setPositiveButton
                }
                saveBrush(processedBitmap, name, groupName)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun validateBrushName(name: String): Boolean {
        if (name.isEmpty()) return false
        val regex = Regex("[\\\\/:*?\"<>|]")
        return !regex.containsMatchIn(name)
    }

    private fun saveBrush(bitmap: Bitmap, name: String, group: String) {
        val id = "custom_${System.currentTimeMillis()}"
        val groupDir = File(BrushTextureHelper.getCustomBrushRoot(), group)
        if (!groupDir.exists()) groupDir.mkdirs()
        val destFile = File(groupDir, "$id.png")

        try {
            FileOutputStream(destFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            BrushTextureHelper.generateThumbnailFile(destFile, this)

            val data = CustomBrushData(
                id = id,
                name = name,
                group = group,
                texturePath = destFile.absolutePath,
                size = (currentSizePixels / CustomBrushData.DIAMETER_BASELINE).coerceIn(0f, 1f),
                flow = 0.8f,
                opacity = currentOpacity,
                spacing = 0.1f,
                rotation = 0,
                rotationRandomness = 0f
            )
            if (BrushTextureHelper.saveCustomBrush(data, this)) {
                ToastUtil.showToast(this, "画笔已创建")
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_SAVED_BRUSH_ID, id)
                }
                setResult(RESULT_BRUSH_SAVED, resultIntent)
                finish()
            } else {
                ToastUtil.showErrorToast(this, "保存配置失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveBrush failed", e)
            ToastUtil.showErrorToast(this, "创建画笔失败")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}