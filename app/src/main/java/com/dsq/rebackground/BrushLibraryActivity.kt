package com.dsq.rebackground

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dsq.rebackground.paint.brush.BrushItem
import com.dsq.rebackground.paint.brush.BrushLibrary
import com.dsq.rebackground.paint.brush.BrushLoader
import com.dsq.rebackground.paint.brush.CustomBrushData
import com.dsq.rebackground.utils.BrushTextureHelper
import com.dsq.rebackground.utils.ToastUtil

import java.io.File

class BrushLibraryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SELECTED_BRUSH_ID = "extra_selected_brush_id"
        private const val TAG = "BrushLibrary"
        private const val GROUP_BUILT_IN = "内置"
        private const val REQUEST_CREATE_BRUSH = 2001
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var groupContainer: LinearLayout
    private lateinit var brushAdapter: BrushAdapter
    private val brushItems = mutableListOf<BrushItem>()

    // 当前选中的分组
    private var currentGroup = GROUP_BUILT_IN
    private val allGroups = mutableListOf<String>()

    // 导入结果回调
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { showImportDialog(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brush_library)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "画笔库"

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        groupContainer = findViewById(R.id.groupContainer)

        findViewById<Button>(R.id.btnImportBrush).setOnClickListener {
            importLauncher.launch("image/*")
        }

        findViewById<Button>(R.id.btnCreateBrush).setOnClickListener {
            val intent = Intent(this, CustomBrushEditorActivity::class.java)
            startActivityForResult(intent, REQUEST_CREATE_BRUSH)
        }

        loadData()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CREATE_BRUSH -> {
                if (resultCode == CustomBrushEditorActivity.RESULT_BRUSH_SAVED) {
                    // 刷新列表
                    loadData()
                }
            }
        }
    }

    private fun loadData() {
        // [MOD PR-2 Step 2] 直接用 BrushLibrary
        brushItems.clear()
        brushItems.addAll(BrushLibrary.allBrushes(this))

        // 构建分组列表
        allGroups.clear()
        allGroups.add(GROUP_BUILT_IN)
        val customGroups = brushItems.map { it.group }.distinct().filter { it != GROUP_BUILT_IN }
        allGroups.addAll(customGroups)

        renderGroups()
        selectGroup(GROUP_BUILT_IN)
    }

    private fun renderGroups() {
        groupContainer.removeAllViews()
        for (group in allGroups) {
            val chip = LayoutInflater.from(this).inflate(R.layout.item_group_chip, groupContainer, false) as LinearLayout
            val icon = chip.findViewById<ImageView>(R.id.groupIcon)
            val name = chip.findViewById<TextView>(R.id.groupName)

            if (group == GROUP_BUILT_IN) {
                icon.setImageResource(R.drawable.ic_star)
            } else {
                icon.setImageResource(R.drawable.ic_folder)
            }
            name.text = group

            chip.isSelected = (group == currentGroup)
            chip.setOnClickListener {
                selectGroup(group)
            }
            if (group != GROUP_BUILT_IN) {
                chip.setOnLongClickListener {
                    showGroupManageDialog(group)
                    true
                }
            }
            groupContainer.addView(chip)
        }
    }

    private fun selectGroup(group: String) {
        currentGroup = group
        for (i in 0 until groupContainer.childCount) {
            val child = groupContainer.getChildAt(i)
            val nameView = child.findViewById<TextView>(R.id.groupName)
            child.isSelected = (nameView.text == group)
        }

        val filtered = brushItems.filter { it.group == group }
        if (!::brushAdapter.isInitialized) {
            brushAdapter = BrushAdapter(filtered) { brushItem ->
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_SELECTED_BRUSH_ID, brushItem.id)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
            recyclerView.adapter = brushAdapter
        } else {
            brushAdapter.updateData(filtered)
        }
    }

    // ========== 导入流程 ==========
    private fun showImportDialog(uri: Uri) {
        val inputName = EditText(this).apply {
            hint = "输入画笔名称（不能包含特殊字符）"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
        AlertDialog.Builder(this)
            .setTitle("导入笔刷")
            .setView(inputName)
            .setPositiveButton("下一步") { _, _ ->
                val name = inputName.text.toString().trim()
                if (validateBrushName(name)) {
                    showGroupPickerForImport(uri, name)
                } else {
                    ToastUtil.showErrorToast(this, "名称包含非法字符，请重新输入")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun validateBrushName(name: String): Boolean {
        if (name.isEmpty()) return false
        val regex = Regex("[\\\\/:*?\"<>|]")
        return !regex.containsMatchIn(name)
    }

    private fun showGroupPickerForImport(uri: Uri, brushName: String) {
        val groups = allGroups.filter { it != GROUP_BUILT_IN }.toMutableList()
        groups.add(0, "新建分组...")
        groups.add("内置")

        AlertDialog.Builder(this)
            .setTitle("选择分组")
            .setItems(groups.toTypedArray()) { _, which ->
                val selected = groups[which]
                when {
                    selected == "新建分组..." -> showNewGroupDialog(uri, brushName)
                    selected == "内置" -> {
                        ToastUtil.showToast(this, "自定义笔刷将导入到内置分组，确认？")
                        importBrush(uri, brushName, selected)
                    }
                    else -> importBrush(uri, brushName, selected)
                }
            }
            .show()
    }

    private fun showNewGroupDialog(uri: Uri, brushName: String) {
        val input = EditText(this).apply {
            hint = "输入分组名称"
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
        AlertDialog.Builder(this)
            .setTitle("新建分组")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val groupName = input.text.toString().trim()
                if (groupName.isEmpty()) {
                    ToastUtil.showErrorToast(this, "分组名称不能为空")
                    return@setPositiveButton
                }
                if (!validateBrushName(groupName)) {
                    ToastUtil.showErrorToast(this, "分组名称包含非法字符")
                    return@setPositiveButton
                }
                if (BrushTextureHelper.createGroup(groupName)) {
                    importBrush(uri, brushName, groupName)
                } else {
                    ToastUtil.showErrorToast(this, "分组创建失败")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importBrush(uri: Uri, brushName: String, groupName: String) {
        // 1. 先导入 PNG，返回唯一 ID（文件名不含扩展名）
        val uniqueId = BrushTextureHelper.importBrushFromUri(this, uri, groupName, brushName)
        if (uniqueId != null) {
            // 2. 构建纹理路径
            val groupDir = File(BrushTextureHelper.getCustomBrushRoot(), groupName)
            val textureFile = File(groupDir, "$uniqueId.png")
            if (textureFile.exists()) {
                // 3. 创建 CustomBrushData（使用默认参数）
                val data = CustomBrushData(
                    id = uniqueId,
                    name = brushName,
                    group = groupName,
                    texturePath = textureFile.absolutePath,
                    size = 0.2f,
                    flow = 0.8f,
                    opacity = 1f,
                    spacing = 0.1f,
                    rotation = 0,
                    rotationRandomness = 0f
                )
                // 4. 保存 JSON 配置
                if (BrushTextureHelper.saveCustomBrush(data, this)) {
                    ToastUtil.showToast(this, "笔刷导入成功")
                    loadData() // 刷新列表
                } else {
                    ToastUtil.showErrorToast(this, "笔刷配置保存失败，但图片已导入")
                    // 可选：删除刚导入的图片
                }
            } else {
                ToastUtil.showErrorToast(this, "导入失败，纹理文件不存在")
            }
        } else {
            ToastUtil.showErrorToast(this, "导入失败，请检查图片格式")
        }
    }

    // ========== 分组管理（长按） ==========
    private fun showGroupManageDialog(group: String) {
        val actions = arrayOf("重命名", "删除分组（含所有笔刷）")
        AlertDialog.Builder(this)
            .setTitle("分组: $group")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(group)
                    1 -> {
                        AlertDialog.Builder(this)
                            .setTitle("确认删除")
                            .setMessage("删除分组 \"$group\" 将同时删除组内所有笔刷，确定？")
                            .setPositiveButton("确定") { _, _ ->
                                if (BrushTextureHelper.deleteGroup(group)) {
                                    ToastUtil.showToast(this, "分组已删除")
                                    loadData()
                                } else {
                                    ToastUtil.showErrorToast(this, "删除失败")
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showRenameGroupDialog(oldName: String) {
        val input = EditText(this).apply {
            setText(oldName)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名分组")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName == oldName) {
                    ToastUtil.showErrorToast(this, "名称无效")
                    return@setPositiveButton
                }
                if (!validateBrushName(newName)) {
                    ToastUtil.showErrorToast(this, "名称包含非法字符")
                    return@setPositiveButton
                }
                if (BrushTextureHelper.renameGroup(oldName, newName)) {
                    ToastUtil.showToast(this, "重命名成功")
                    loadData()
                } else {
                    ToastUtil.showErrorToast(this, "重命名失败")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // ========== 笔刷适配器 ==========

    private inner class BrushAdapter(
        private var items: List<BrushItem>,
        private val onItemClick: (BrushItem) -> Unit
    ) : RecyclerView.Adapter<BrushAdapter.BrushViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrushViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_brush_library, parent, false)
            return BrushViewHolder(view)
        }

        override fun onBindViewHolder(holder: BrushViewHolder, position: Int) {
            holder.bind(items[position], onItemClick)
        }

        override fun getItemCount(): Int = items.size

        fun updateData(newItems: List<BrushItem>) {
            this.items = newItems
            notifyDataSetChanged()
        }

        inner class BrushViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView = itemView.findViewById<ImageView>(R.id.brush_icon)
            private val nameView = itemView.findViewById<TextView>(R.id.brush_name)
            private val deleteBtn = itemView.findViewById<ImageButton>(R.id.btnDelete)
            private val groupTag = itemView.findViewById<TextView>(R.id.brush_group_tag)

            fun bind(brush: BrushItem, onClick: (BrushItem) -> Unit) {
                nameView.text = brush.displayName

                val thumbnail = if (brush.thumbnailPath != null) {
                    BitmapFactory.decodeFile(brush.thumbnailPath)
                } else {
                    generateThumbnailFromConfig(brush)
                }
                if (thumbnail != null) {
                    iconView.setImageBitmap(thumbnail)
                } else {
                    iconView.setImageResource(R.drawable.ic_brush_default)
                }
                iconView.setBackgroundResource(R.drawable.brush_icon_border)

                if (!brush.isPreset) {
                    deleteBtn.visibility = View.VISIBLE
                    groupTag.visibility = View.VISIBLE
                    groupTag.text = brush.group
                    deleteBtn.setOnClickListener {
                        AlertDialog.Builder(itemView.context)
                            .setTitle("删除笔刷")
                            .setMessage("确定删除 \"${brush.displayName}\" ？")
                            .setPositiveButton("确定") { _, _ ->
                                if (BrushTextureHelper.deleteBrush(itemView.context, brush)) {
                                    ToastUtil.showToast(itemView.context, "已删除")
                                    (itemView.context as? BrushLibraryActivity)?.loadData()
                                } else {
                                    ToastUtil.showErrorToast(itemView.context, "删除失败")
                                }
                            }
                            .setNegativeButton("取消", null)
                            .show()
                    }

                    // 长按显示编辑和删除菜单
                    itemView.setOnLongClickListener {
                        AlertDialog.Builder(itemView.context)
                            .setTitle("操作")
                            .setItems(arrayOf("编辑", "删除")) { _, which ->
                                when (which) {
                                    0 -> {
                                        // 编辑
                                        val intent = Intent(itemView.context, CustomBrushEditorActivity::class.java).apply {
                                            putExtra(CustomBrushEditorActivity.EXTRA_BRUSH_ID, brush.id)
                                        }

                                        (itemView.context as? BrushLibraryActivity)?.startActivityForResult(intent, REQUEST_CREATE_BRUSH)
                                    }
                                    1 -> {
                                        // 删除：调用已有的删除逻辑
                                        deleteBtn.performClick()
                                    }
                                }
                            }
                            .show()
                        true
                    }
                } else {
                    deleteBtn.visibility = View.GONE
                    groupTag.visibility = View.GONE
                    // 预设画笔无长按菜单
                }

                itemView.setOnClickListener { onClick(brush) }
            }

            private fun generateThumbnailFromConfig(brush: BrushItem): android.graphics.Bitmap? {
                // [MOD PR-2 Step 2] 从 texturePath 加载，不再读 config.stamp
                val path = brush.texturePath ?: return null
                val bitmap = BrushLoader.loadTextureFromPath(itemView.context, path) ?: return null
                return BrushTextureHelper.generateThumbnail(bitmap)
            }
        }
    }
}