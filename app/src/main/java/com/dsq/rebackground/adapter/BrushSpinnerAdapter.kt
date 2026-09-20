package com.dsq.rebackground.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.dsq.rebackground.R
import com.dsq.rebackground.paint.brush.BrushItem
import com.dsq.rebackground.paint.brush.BrushLoader
import com.dsq.rebackground.utils.BrushTextureHelper

/**
 * 画笔 Spinner 适配器（PR-2 Step 4 已切换至 paint.brush.BrushItem）
 *
 * 缩略图加载优先级（与 BrushLibraryActivity / CustomBrushEditorActivity 保持一致）：
 *   1. thumbnailPath（缓存的缩略图文件）
 *   2. texturePath（从 asset 或绝对路径加载后缩放）
 *   3. 默认图标
 */
class BrushSpinnerAdapter(
    private val context: Context,
    private var items: List<BrushItem>
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): BrushItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return createItemView(convertView, parent, position, true)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return createItemView(convertView, parent, position, false)
    }

    private fun createItemView(convertView: View?, parent: ViewGroup?, position: Int, isSelected: Boolean): View {
        val view = convertView ?: inflater.inflate(R.layout.item_brush_spinner, parent, false)
        val iconView = view.findViewById<ImageView>(R.id.brush_icon)
        val nameView = view.findViewById<TextView>(R.id.brush_name)
        val groupView = view.findViewById<TextView>(R.id.brush_group)

        val item = getItem(position)

        // 加载缩略图（三级兜底）
        loadThumbnail(item, iconView)

        nameView.text = item.displayName
        if (isSelected) {
            groupView.visibility = View.GONE
        } else {
            groupView.visibility = View.VISIBLE
            groupView.text = item.group
        }

        return view
    }

    private fun loadThumbnail(item: BrushItem, iconView: ImageView) {
        // 1. 缓存缩略图优先
        val thumbPath = item.thumbnailPath
        if (thumbPath != null) {
            val bitmap = BitmapFactory.decodeFile(thumbPath)
            if (bitmap != null) {
                iconView.setImageBitmap(bitmap)
                return
            }
        }

        // 2. 从 texturePath 加载
        val texturePath = item.texturePath
        if (texturePath != null) {
            val bitmap = BrushLoader.loadTextureFromPath(context, texturePath)
            if (bitmap != null) {
                iconView.setImageBitmap(BrushTextureHelper.generateThumbnail(bitmap))
                return
            }
        }

        // 3. 兜底：默认图标（电子画笔等程序化笔刷走这里）
        iconView.setImageResource(R.drawable.ic_brush_default)
    }

    fun updateData(newItems: List<BrushItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}