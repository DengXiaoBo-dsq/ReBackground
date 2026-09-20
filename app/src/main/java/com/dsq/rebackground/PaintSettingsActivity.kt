package com.dsq.rebackground

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.dsq.rebackground.utils.ToastUtil
import kotlin.math.roundToInt

class PaintSettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_IMAGE = 1003
        private const val TAG = "PaintSettings"
        // 用于传递参数
        const val EXTRA_FLOW = "extra_flow"
        const val EXTRA_OPACITY = "extra_opacity"
        const val EXTRA_ROTATION = "extra_rotation"
        const val EXTRA_TEXTURE_INDEX = "extra_texture_index"

        // 导出参数
        const val EXTRA_EXPORT_FORMAT = "extra_export_format"
        const val EXTRA_EXPORT_FILENAME = "extra_export_filename"

        // 导入草稿参数
        const val EXTRA_IMPORT_DRAFT = "extra_import_draft"
        const val EXTRA_DRAFT_URI = "extra_draft_uri"
    }

    private lateinit var flowSeekBar: SeekBar
    private lateinit var opacitySeekBar: SeekBar
    private lateinit var rotationSwitch: SwitchCompat
    private lateinit var textureSpinner: Spinner
    private lateinit var exportBtn: Button
    private lateinit var importTextureBtn: Button
    private lateinit var importDraftBtn: Button

    private var currentFlow = 0.5f
    private var currentOpacity = 0.99f
    private var currentRotation = true
    private var currentTextureIndex = 0

    // 导入草稿的文件选择器
    private val importDraftLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            // 返回结果给主界面
            val resultIntent = Intent().apply {
                putExtra(EXTRA_IMPORT_DRAFT, true)
                putExtra(EXTRA_DRAFT_URI, it.toString())
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paint_settings)

        // 读取传入的参数
        currentFlow = intent.getFloatExtra(EXTRA_FLOW, 0.5f)
        currentOpacity = intent.getFloatExtra(EXTRA_OPACITY, 0.99f)
        currentRotation = intent.getBooleanExtra(EXTRA_ROTATION, true)
        currentTextureIndex = intent.getIntExtra(EXTRA_TEXTURE_INDEX, 0)

        // 初始化控件
        flowSeekBar = findViewById(R.id.settingsFlowSeekBar)
        opacitySeekBar = findViewById(R.id.settingsOpacitySeekBar)
        rotationSwitch = findViewById(R.id.settingsRotationSwitch)
        textureSpinner = findViewById(R.id.settingsTextureSpinner)
        exportBtn = findViewById(R.id.settingsExportBtn)
        importTextureBtn = findViewById(R.id.settingsImportTextureBtn)
        importDraftBtn = findViewById(R.id.settingsImportDraftBtn)

        // 设置初始值
        flowSeekBar.progress = (currentFlow * 99).roundToInt()
        opacitySeekBar.progress = (currentOpacity * 99).roundToInt()
        rotationSwitch.isChecked = currentRotation
        textureSpinner.setSelection(currentTextureIndex)

        // 工具栏返回
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"

        // 监听变化
        flowSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentFlow = progress / 99f
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentOpacity = progress / 99f
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        rotationSwitch.setOnCheckedChangeListener { _, isChecked ->
            currentRotation = isChecked
        }

        textureSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTextureIndex = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 导出按钮 -> 弹出选择对话框
        exportBtn.setOnClickListener {
            showExportDialog()
        }

        // 导入纹理
        importTextureBtn.setOnClickListener {
            pickImageFromGallery()
        }

        // 导入草稿
        importDraftBtn.setOnClickListener {
            importDraftLauncher.launch("*/*")  // 实际应限制为 .rebrush，但这里先允许所有文件
        }
    }

    // ========== 导出对话框 ==========
    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export, null)
        val fileNameInput = dialogView.findViewById<EditText>(R.id.exportFileName)
        val formatSpinner = dialogView.findViewById<Spinner>(R.id.exportFormatSpinner)

        // 更新格式列表，包含所有支持格式
        val formats = arrayOf(
            "PNG (带透明通道)",
            "JPEG (高质量)",
            "WebP (高效压缩)",
            "草稿 (.rebrush)",
            "PSD (带图层信息)"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, formats)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        formatSpinner.adapter = adapter

        val defaultName = "ReBackground_${System.currentTimeMillis()}"
        fileNameInput.setText(defaultName)

        AlertDialog.Builder(this)
            .setTitle("导出图片")
            .setView(dialogView)
            .setPositiveButton("导出") { _, _ ->
                val name = fileNameInput.text.toString().trim()
                if (name.isEmpty()) {
                    ToastUtil.showErrorToast(this, "文件名不能为空")
                    return@setPositiveButton
                }
                val formatIndex = formatSpinner.selectedItemPosition
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_EXPORT_FILENAME, name)
                    putExtra(EXTRA_EXPORT_FORMAT, formatIndex)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                    if (bitmap != null) {
                        ToastUtil.showToast(this, "纹理已导入（请在主界面应用）")
                        // 可暂存，返回后通过 Intent 传递
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "纹理导入失败", e)
                    ToastUtil.showToast(this, "纹理导入失败")
                }
            }
        }
    }

    override fun onBackPressed() {
        // 返回时携带最新设置
        val resultIntent = Intent().apply {
            putExtra(EXTRA_FLOW, currentFlow)
            putExtra(EXTRA_OPACITY, currentOpacity)
            putExtra(EXTRA_ROTATION, currentRotation)
            putExtra(EXTRA_TEXTURE_INDEX, currentTextureIndex)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
        super.onBackPressed()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}