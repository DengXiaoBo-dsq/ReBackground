//package com.dsq.rebackground.utils;
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.Color;
//import android.graphics.Matrix;
//import android.graphics.Paint;
//import android.graphics.Path;
//import android.graphics.PorterDuff;
//import android.graphics.PorterDuffXfermode;
//import android.util.AttributeSet;
//import android.view.MotionEvent;
//import android.widget.ImageView;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.Queue;
//
//public class MaskDrawView extends ImageView {
//    private Bitmap mOriginalBitmap;   // 原始图片
//    private Bitmap mMaskBitmap;       // 掩码图层
//    private Canvas mMaskCanvas;       // 掩码画布
//
//    private Path mCurrentPath;        // 当前绘制路径
//    private Paint mPaint;             // 画笔配置
//    private Matrix mTransformMatrix = new Matrix();  // 图片变换矩阵
//    private Matrix mInverseMatrix = new Matrix();    // 逆矩阵（用于坐标转换）
//
//    private int mBrushColor = Color.RED; // 画笔颜色
//    private int mBrushSize = 20;         // 画笔大小
//    private boolean mIsPainting = true;  // 模式（true: 画笔，false: 橡皮擦）
//
//    public MaskDrawView(Context context) {
//        super(context);
//        init();
//    }
//
//    public MaskDrawView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init();
//    }
//
//    private void init() {
//        // 初始化画笔
//        mPaint = new Paint();
//        mPaint.setAntiAlias(true);  // 启用抗锯齿
//        mPaint.setStyle(Paint.Style.STROKE);  // 设置绘制样式为描边
//        mPaint.setStrokeJoin(Paint.Join.ROUND);  // 设置连接点为圆角
//        mPaint.setStrokeCap(Paint.Cap.ROUND);  // 设置端点为圆角
//        mPaint.setColor(mBrushColor);  // 设置画笔颜色
//        mPaint.setStrokeWidth(mBrushSize);  // 设置画笔宽度
//        mPaint.setAlpha(255);  // 设置透明度（可选）
//
//        mCurrentPath = new Path();
//    }
//
//    /**
//     * 设置原始图片并初始化掩码
//     */
//    public void setOriginalBitmap(Bitmap originalBitmap) {
//        if (originalBitmap == null) return;
//        this.mOriginalBitmap = originalBitmap;
//
//        // 初始化掩码（与原始图片同尺寸）
//        mMaskBitmap = Bitmap.createBitmap(
//                originalBitmap.getWidth(),
//                originalBitmap.getHeight(),
//                Bitmap.Config.ARGB_8888
//        );
//        mMaskCanvas = new Canvas(mMaskBitmap);
//
//        // 显示原始图片
//        setImageBitmap(originalBitmap);
//    }
//
//
//    @Override
//    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
//        super.onSizeChanged(w, h, oldw, oldh);
//        setupFitCenterMatrix(); // 视图尺寸变化时重新计算矩阵
//    }
//
//    /**
//     * 计算并应用fitCenter模式的变换矩阵
//     */
//    private void setupFitCenterMatrix() {
//        if (mOriginalBitmap == null || getWidth() == 0 || getHeight() == 0) return;
//
//        int viewWidth = getWidth();
//        int viewHeight = getHeight();
//        int bitmapWidth = mOriginalBitmap.getWidth();
//        int bitmapHeight = mOriginalBitmap.getHeight();
//
//        Matrix matrix = new Matrix();
//        float scale = Math.min(
//                (float) viewWidth / bitmapWidth,
//                (float) viewHeight / bitmapHeight
//        );
//
//        matrix.postScale(scale, scale);
//        // 居中平移
//        float dx = (viewWidth - bitmapWidth * scale) / 2;
//        float dy = (viewHeight - bitmapHeight * scale) / 2;
//        matrix.postTranslate(dx, dy);
//
//        // 应用矩阵并更新逆矩阵
//        setImageMatrix(matrix);
//        mTransformMatrix.set(matrix);
//        matrix.invert(mInverseMatrix);
//    }
//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//
//        // 1. 绘制原始图片（父类 ImageView 已处理）
//        // 2. 绘制掩码层
//        if (mMaskBitmap != null && !mMaskBitmap.isRecycled()) {
//            canvas.drawBitmap(mMaskBitmap, mTransformMatrix, null);
//        }
//        // 3. 实时绘制当前路径（预览）
////        canvas.drawPath(mCurrentPath, mPaint);
//    }
//
//
//    /**
//     * 获取最终掩码（供模型使用）
//     */
//    public Bitmap getMaskBitmap() {
//        if (mMaskBitmap == null || mMaskBitmap.isRecycled()) {
//            return null;
//        }
//        return mMaskBitmap.copy(Bitmap.Config.ARGB_8888, false);
//    }
//
//    /**
//     * 清除所有涂抹痕迹
//     */
//    public void clearMask() {
//        if (mMaskCanvas != null) {
//            mMaskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
//            invalidate();
//            // 显示原始图片
//            setImageBitmap(this.mOriginalBitmap);
//        }
//    }
//
//    /**
//     * 设置画笔大小
//     */
//    public void setBrushSize(int size) {
//        mBrushSize = size;
//        mPaint.setStrokeWidth(size);
//    }
//
//    /**
//     * 设置画笔颜色
//     */
//    public void setBrushColor(int color) {
//        mBrushColor = color;
//        mPaint.setColor(color);
//    }
//
//    /**
//     * 设置模式（画笔/橡皮擦）
//     */
//    public void setPaintingMode(boolean isPainting) {
//        mIsPainting = isPainting;
//        if (mIsPainting) {
//            mPaint.setXfermode(null); // 画笔模式
//        } else {
//            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); // 橡皮擦模式
//        }
//    }
//
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        if (mOriginalBitmap == null) return false;
//
//        // 获取触摸点坐标（已考虑图片缩放和平移）
//        float[] point = {event.getX(), event.getY()};
//        mInverseMatrix.mapPoints(point);
//        float x = point[0];
//        float y = point[1];
//
//        switch (event.getAction()) {
//            case MotionEvent.ACTION_DOWN:
//
//                mCurrentPath.reset();  // 重置路径，开始新的绘制
//                mCurrentPath.moveTo(x, y);  // 移动到触摸点位置
//                invalidate();  // 确保触摸开始时立即刷新界面
//                break;
//            case MotionEvent.ACTION_MOVE:
//                mMaskCanvas.drawPath(mCurrentPath, mPaint);
//                mCurrentPath.lineTo(x, y);  // 继续绘制路径
//                invalidate();  // 立即刷新界面，显示当前路径
//                break;
//            case MotionEvent.ACTION_UP:
//                // 将路径绘制到掩码图层
//                mMaskCanvas.drawPath(mCurrentPath, mPaint);
//                mCurrentPath.reset();  // 重置路径，准备下一次绘制
//                invalidate();  // 刷新视图，显示更新后的掩码
//                break;
//        }
//        return true;
//    }
//
////=========================================
//    /** 核心优化：高性能水印区域检测 */
//    public List<int[]> getWatermarkRegions() {
//        if (mMaskBitmap == null) return new ArrayList<>();
//
//        // 1. 使用两遍扫描算法快速获取初始区域
//        List<int[]> rawAreas = getConnectedAreas();
//
//        // 2. 如果图片最大边≤400，直接返回整个区域
//        int maxDimension = Math.max(mMaskBitmap.getWidth(), mMaskBitmap.getHeight());
//        if (maxDimension <= 400) {
//            return Arrays.asList(new int[]{0, 0, mMaskBitmap.getWidth(), mMaskBitmap.getHeight()});
//        }
//
//        // 3. 空间网格加速的区域合并
//        return mergeRegionsWithSpatialGrid(rawAreas, 110, 400);
//    }
//
//    /** 两遍扫描连通标记算法（Two-pass algorithm） */
//    private List<int[]> getConnectedAreas() {
//        int width = mMaskBitmap.getWidth();
//        int height = mMaskBitmap.getHeight();
//        int[] labels = new int[width * height];
//        Arrays.fill(labels, -1);
//        UnionFind uf = new UnionFind();
//
//        // First pass: 标记连通区域
//        int currentLabel = 0;
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                if (mMaskBitmap.getPixel(x, y) != mBrushColor) continue;
//
//                int[] neighbors = getNeighborLabels(x, y, width, labels);
//                if (neighbors.length == 0) {
//                    labels[y * width + x] = currentLabel++;
//                } else {
//                    int minLabel = Arrays.stream(neighbors).min().getAsInt();
//                    labels[y * width + x] = minLabel;
//                    for (int label : neighbors) {
//                        if (label != minLabel) uf.union(minLabel, label);
//                    }
//                }
//            }
//        }
//
//        // Second pass: 合并等效标签并计算边界
//        Map<Integer, int[]> regionMap = new HashMap<>();
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                int idx = y * width + x;
//                if (labels[idx] == -1) continue;
//
//                int root = uf.find(labels[idx]);
//                int[] bounds = regionMap.getOrDefault(root,
//                        new int[]{x, y, x, y});
//                bounds[0] = Math.min(bounds[0], x);
//                bounds[1] = Math.min(bounds[1], y);
//                bounds[2] = Math.max(bounds[2], x);
//                bounds[3] = Math.max(bounds[3], y);
//                regionMap.put(root, bounds);
//            }
//        }
//
//        return new ArrayList<>(regionMap.values());
//    }
//
//    /** 获取相邻像素标签（优化内存访问模式） */
//    private int[] getNeighborLabels(int x, int y, int width, int[] labels) {
//        int[] neighbors = new int[2]; // 最多左、上两个邻居
//        int count = 0;
//
//        // 左邻居
//        if (x > 0 && labels[y * width + (x - 1)] != -1) {
//            neighbors[count++] = labels[y * width + (x - 1)];
//        }
//
//        // 上邻居
//        if (y > 0 && labels[(y - 1) * width + x] != -1) {
//            neighbors[count++] = labels[(y - 1) * width + x];
//        }
//
//        return Arrays.copyOf(neighbors, count);
//    }
//
//    /** 基于空间网格的区域合并（时间复杂度 O(n)） */
//    private List<int[]> mergeRegionsWithSpatialGrid(List<int[]> regions,
//                                                    int mergeThreshold,
//                                                    int maxSize) {
//        // 1. 构建空间网格索引（200x200像素网格）
//        Map<String, List<int[]>> gridMap = new HashMap<>();
//        int gridSize = 200;
//        for (int[] region : regions) {
//            int gridX = region[0] / gridSize;
//            int gridY = region[1] / gridSize;
//            String key = gridX + "," + gridY;
//            gridMap.computeIfAbsent(key, k -> new ArrayList<>()).add(region);
//        }
//
//        // 2. 仅检查相邻网格的区域
//        List<int[]> mergedRegions = new ArrayList<>();
//        boolean[] merged = new boolean[regions.size()];
//        for (int i = 0; i < regions.size(); i++) {
//            if (merged[i]) continue;
//            int[] current = regions.get(i);
//            int[] mergedArea = current.clone();
//
//            // 3. 获取当前区域所在的网格和相邻网格
//            int gridX = current[0] / gridSize;
//            int gridY = current[1] / gridSize;
//            for (int dx = -1; dx <= 1; dx++) {
//                for (int dy = -1; dy <= 1; dy++) {
//                    String key = (gridX + dx) + "," + (gridY + dy);
//                    List<int[]> candidates = gridMap.getOrDefault(key, new ArrayList<>());
//                    for (int j = 0; j < candidates.size(); j++) {
//                        int[] other = candidates.get(j);
//                        if (merged[j] || !shouldMerge(mergedArea, other, mergeThreshold)) continue;
//
//                        // 4. 快速合并区域边界
//                        mergedArea[0] = Math.min(mergedArea[0], other[0]);
//                        mergedArea[1] = Math.min(mergedArea[1], other[1]);
//                        mergedArea[2] = Math.max(mergedArea[2], other[2]);
//                        mergedArea[3] = Math.max(mergedArea[3], other[3]);
//                        merged[j] = true;
//                    }
//                }
//            }
//            mergedRegions.add(mergedArea);
//        }
//
//        // 5. 过滤尺寸过大的区域
//        List<int[]> finalRegions = new ArrayList<>();
//        for (int[] region : mergedRegions) {
//            if ((region[2] - region[0] <= maxSize) &&
//                    (region[3] - region[1] <= maxSize)) {
//                finalRegions.add(region);
//            } else {
//                // 对超限区域进行二次分割（可选）
//                finalRegions.add(region);
//            }
//        }
//
//        return finalRegions;
//    }
//
//    /** 快速区域合并判断（提前短路无效比较） */
//    private boolean shouldMerge(int[] a, int[] b, int threshold) {
//        // 快速失败条件
//        if (a[2] + threshold < b[0] || b[2] + threshold < a[0]) return false;
//        if (a[3] + threshold < b[1] || b[3] + threshold < a[1]) return false;
//
//        // 精确计算最小间距
//        int xGap = Math.max(0, Math.max(a[0] - b[2], b[0] - a[2]));
//        int yGap = Math.max(0, Math.max(a[1] - b[3], b[1] - a[3]));
//        return (xGap < threshold) && (yGap < threshold);
//    }
//
//    /** 并查集数据结构（路径压缩优化） */
//    private static class UnionFind {
//        private final Map<Integer, Integer> parent = new HashMap<>();
//
//        public int find(int x) {
//            if (!parent.containsKey(x)) parent.put(x, x);
//            while (parent.get(x) != x) {
//                parent.put(x, parent.get(parent.get(x))); // 路径压缩
//                x = parent.get(x);
//            }
//            return x;
//        }
//
//        public void union(int x, int y) {
//            int rootX = find(x);
//            int rootY = find(y);
//            if (rootX != rootY) {
//                parent.put(rootY, rootX);
//            }
//        }
//    }
//
//    //========================================================
//    @Override
//    public void setImageMatrix(Matrix matrix) {
//        super.setImageMatrix(matrix);
//        // 记录变换矩阵及其逆矩阵
//        mTransformMatrix.set(matrix);
//        matrix.invert(mInverseMatrix);
//    }
//
//    /**
//     * 释放资源
//     */
//    public void recycle() {
//        if (mMaskBitmap != null && !mMaskBitmap.isRecycled()) {
//            mMaskBitmap.recycle();
//            mMaskBitmap = null;
//        }
//        if (mOriginalBitmap != null && !mOriginalBitmap.isRecycled()) {
//            mOriginalBitmap.recycle();
//            mOriginalBitmap = null;
//        }
//    }
//}

//
//package com.dsq.rebackground.utils;
//
//import android.content.Context;
//import android.graphics.*;
//import android.util.AttributeSet;
//import android.util.Log;
//import android.view.MotionEvent;
//import android.widget.ImageView;
//
//import java.util.*;
//import java.util.concurrent.*;
//
//public class MaskDrawView extends ImageView {
//    private Bitmap mOriginalBitmap;   // 原始图片
//    private Bitmap mMaskBitmap;       // 掩码图层
//    private Canvas mMaskCanvas;       // 掩码画布
//
//    private Path mCurrentPath;        // 当前绘制路径
//    private Paint mPaint;             // 画笔配置
//    private Matrix mTransformMatrix = new Matrix();  // 图片变换矩阵
//    private Matrix mInverseMatrix = new Matrix();    // 逆矩阵（用于坐标转换）
//    private List<int[]> mWatermarkRegions;
//    private int mBrushColor = Color.RED; // 画笔颜色
////    private int mBrushColor = 0xFFFF0000; // 不透明红色（掩码用）
////    private int mDisplayColor = 0x66FF0000; // 半透明红色（显示用，Alpha=0.4）
//    private int mBrushSize = 20;         // 画笔大小
//    private boolean mIsPainting = true;  // 模式（true: 画笔，false: 橡皮擦）
//
//    public MaskDrawView(Context context) {
//        super(context);
//        init();
//    }
//
//    public MaskDrawView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//        init();
//    }
//
//    private void init() {
//        // 初始化画笔
//        mPaint = new Paint();
//        mPaint.setAntiAlias(true);  // 启用抗锯齿
//        mPaint.setStyle(Paint.Style.STROKE);  // 设置绘制样式为描边
//        mPaint.setStrokeJoin(Paint.Join.ROUND);  // 设置连接点为圆角
//        mPaint.setStrokeCap(Paint.Cap.ROUND);  // 设置端点为圆角
//        mPaint.setColor(mBrushColor);
////        mPaint.setColor(mDisplayColor); // 设置显示颜色
////        mPaint.setAlpha(102); // 设置透明度为0.4（102/255≈0.4）// 设置画笔颜色
//        mPaint.setStrokeWidth(mBrushSize);  // 设置画笔宽度
//
//        mCurrentPath = new Path();
//    }
//
//    /**
//     * 设置原始图片并初始化掩码
//     */
//    public void setOriginalBitmap(Bitmap originalBitmap) {
//        if (originalBitmap == null) return;
//        this.mOriginalBitmap = originalBitmap;
//
//        // 初始化掩码（与原始图片同尺寸）
//        mMaskBitmap = Bitmap.createBitmap(
//                originalBitmap.getWidth(),
//                originalBitmap.getHeight(),
//                Bitmap.Config.ARGB_8888
//        );
//        mMaskCanvas = new Canvas(mMaskBitmap);
//
//        // 显示原始图片
//        setImageBitmap(originalBitmap);
//    }
//
//    @Override
//    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
//        super.onSizeChanged(w, h, oldw, oldh);
//        setupFitCenterMatrix(); // 视图尺寸变化时重新计算矩阵
//    }
//
//    /**
//     * 计算并应用fitCenter模式的变换矩阵
//     */
//    private void setupFitCenterMatrix() {
//        if (mOriginalBitmap == null || getWidth() == 0 || getHeight() == 0) return;
//
//        int viewWidth = getWidth();
//        int viewHeight = getHeight();
//        int bitmapWidth = mOriginalBitmap.getWidth();
//        int bitmapHeight = mOriginalBitmap.getHeight();
//
//        Matrix matrix = new Matrix();
//        float scale = Math.min(
//                (float) viewWidth / bitmapWidth,
//                (float) viewHeight / bitmapHeight
//        );
//
//        matrix.postScale(scale, scale);
//        // 居中平移
//        float dx = (viewWidth - bitmapWidth * scale) / 2;
//        float dy = (viewHeight - bitmapHeight * scale) / 2;
//        matrix.postTranslate(dx, dy);
//
//        // 应用矩阵并更新逆矩阵
//        setImageMatrix(matrix);
//        mTransformMatrix.set(matrix);
//        matrix.invert(mInverseMatrix);
//    }
//
//    @Override
//    protected void onDraw(Canvas canvas) {
//        super.onDraw(canvas);
//
//        // 1. 绘制原始图片（父类 ImageView 已处理）
//        // 2. 绘制掩码层
//        if (mMaskBitmap != null && !mMaskBitmap.isRecycled()) {
//            canvas.drawBitmap(mMaskBitmap, mTransformMatrix, null);
//        }
//    }
//
//    /**
//     * 获取最终掩码（供模型使用）
//     */
//    public Bitmap getMaskBitmap() {
//        if (mMaskBitmap == null || mMaskBitmap.isRecycled()) {
//            return null;
//        }
//        return mMaskBitmap.copy(Bitmap.Config.ARGB_8888, false);
//    }
//
//
//    public void clearMask() {
//        clearWatermarkRegions();
//
//        if (mOriginalBitmap != null && !mOriginalBitmap.isRecycled()) {
//            // 重新创建掩码位图和画布（确保像素完全透明）
//            mMaskBitmap = Bitmap.createBitmap(
//                    mOriginalBitmap.getWidth(),
//                    mOriginalBitmap.getHeight(),
//                    Bitmap.Config.ARGB_8888
//            );
//            mMaskCanvas = new Canvas(mMaskBitmap);
//            mMaskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); // 确保所有像素透明
//
//            // 刷新视图
//            invalidate();
//
//            // 显示原始图片
//            setImageBitmap(mOriginalBitmap);
//        }
//    }
//    /**
//     * 设置画笔大小
//     */
//    public void setBrushSize(int size) {
//        mBrushSize = size;
//        mPaint.setStrokeWidth(size);
//    }
//
//    /**
//     * 设置画笔颜色
//     */
//    public void setBrushColor(int color) {
//        mBrushColor = color;
//        mPaint.setColor(color);
//    }
//
//    /**
//     * 设置模式（画笔/橡皮擦）
//     */
//    public void setPaintingMode(boolean isPainting) {
//        mIsPainting = isPainting;
//        if (mIsPainting) {
//            mPaint.setXfermode(null); // 画笔模式
//        } else {
//            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); // 橡皮擦模式
//        }
//    }
//
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        if (mOriginalBitmap == null) return false;
//
//        // 获取触摸点坐标（已考虑图片缩放和平移）
//        float[] point = {event.getX(), event.getY()};
//        mInverseMatrix.mapPoints(point);
//        float x = point[0];
//        float y = point[1];
//
//        switch (event.getAction()) {
//            case MotionEvent.ACTION_DOWN:
//                mCurrentPath.reset();  // 重置路径，开始新的绘制
//                mCurrentPath.moveTo(x, y);  // 移动到触摸点位置
//                invalidate();  // 确保触摸开始时立即刷新界面
//                break;
//            case MotionEvent.ACTION_MOVE:
//                mMaskCanvas.drawPath(mCurrentPath, mPaint);
//                mCurrentPath.lineTo(x, y);  // 继续绘制路径
//                invalidate();  // 立即刷新界面，显示当前路径
//                break;
//            case MotionEvent.ACTION_UP:
//                // 将路径绘制到掩码图层
//                mMaskCanvas.drawPath(mCurrentPath, mPaint);
//                mCurrentPath.reset();  // 重置路径，准备下一次绘制
//                invalidate();  // 刷新视图，显示更新后的掩码
//                break;
//        }
//        return true;
//    }
//
//    //=========================================
//    /** 核心优化：高性能水印区域检测 */
//
//    public void clearWatermarkRegions() {
//        if (mWatermarkRegions != null) {
//            mWatermarkRegions.clear();
//        } else {
//            mWatermarkRegions = new ArrayList<>();
//        }
//        Log.d("清理功能：", "水印区列表已清空");
//    }
//
//    public List<int[]> getWatermarkRegions() {
//        if (mMaskBitmap == null) return new ArrayList<>();
//
//        // 每次调用时清空并重新计算
//        clearWatermarkRegions(); // 清空缓存
//        mWatermarkRegions = new ArrayList<>(); // 初始化新列表
//
//        List<int[]> rawAreas = getConnectedAreas();
//        int maxDimension = Math.max(mMaskBitmap.getWidth(), mMaskBitmap.getHeight());
//        if (maxDimension <= 400) {
//            mWatermarkRegions.add(new int[]{0, 0, mMaskBitmap.getWidth(), mMaskBitmap.getHeight()});
//        } else {
//            mWatermarkRegions = mergeRegionsWithSpatialGrid(rawAreas, 110, 400);
//        }
//
//        return mWatermarkRegions;
//    }
//
//
//    private List<int[]> getConnectedAreas() {
//        int width = mMaskBitmap.getWidth();
//        int height = mMaskBitmap.getHeight();
//        int[] pixels = new int[width * height];
//        mMaskBitmap.getPixels(pixels, 0, width, 0, 0, width, height); // 一次性读取像素数据
//
//        int[] labels = new int[width * height];
//        Arrays.fill(labels, -1);
//        UnionFind uf = new UnionFind(width * height);
//
//        // First pass: 标记连通区域
//        int currentLabel = 0;
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                if (pixels[y * width + x] != mBrushColor) continue;
//
//                int[] neighbors = getNeighborLabels(x, y, width, labels);
//                if (neighbors.length == 0) {
//                    labels[y * width + x] = currentLabel++;
//                } else {
//                    int minLabel = neighbors[0];
//                    for (int label : neighbors) {
//                        if (label < minLabel) minLabel = label;
//                    }
//                    labels[y * width + x] = minLabel;
//                    for (int label : neighbors) {
//                        if (label != minLabel) uf.union(minLabel, label);
//                    }
//                }
//            }
//        }
//
//
//        // Second pass: 合并等效标签并计算边界
//        Map<Integer, int[]> regionMap = new HashMap<>();
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x++) {
//                int idx = y * width + x;
//                if (labels[idx] == -1) continue;
//
//                int root = uf.find(labels[idx]);
//                int[] bounds = regionMap.getOrDefault(root, new int[]{x, y, x, y});
//                bounds[0] = Math.min(bounds[0], x);
//                bounds[1] = Math.min(bounds[1], y);
//                bounds[2] = Math.max(bounds[2], x);
//                bounds[3] = Math.max(bounds[3], y);
//                regionMap.put(root, bounds);
//            }
//        }
//
//        return new ArrayList<>(regionMap.values());
//    }
//
//    /** 获取相邻像素标签（优化内存访问模式） */
//    private int[] getNeighborLabels(int x, int y, int width, int[] labels) {
//        int[] neighbors = new int[4]; // 左、上、右、下四个邻居
//        int count = 0;
//
//        // 左邻居
//        if (x > 0 && labels[y * width + (x - 1)] != -1) {
//            neighbors[count++] = labels[y * width + (x - 1)];
//        }
//
//        // 上邻居
//        if (y > 0 && labels[(y - 1) * width + x] != -1) {
//            neighbors[count++] = labels[(y - 1) * width + x];
//        }
//
//        // 右邻居
//        if (x < width - 1 && labels[y * width + (x + 1)] != -1) {
//            neighbors[count++] = labels[y * width + (x + 1)];
//        }
//
//        // 下邻居
//        if (y < labels.length / width - 1 && labels[(y + 1) * width + x] != -1) {
//            neighbors[count++] = labels[(y + 1) * width + x];
//        }
//
//        return Arrays.copyOf(neighbors, count);
//    }
//
//    /** 基于空间网格的区域合并（时间复杂度 O(n)） */
//    private List<int[]> mergeRegionsWithSpatialGrid(List<int[]> regions, int mergeThreshold, int maxSize) {
//        // 1. 构建空间网格索引（200x200像素网格）
//        Map<String, List<int[]>> gridMap = new HashMap<>();
//        int gridSize = 200;
//        for (int[] region : regions) {
//            int gridX = region[0] / gridSize;
//            int gridY = region[1] / gridSize;
//            String key = gridX + "," + gridY;
//            gridMap.computeIfAbsent(key, k -> new ArrayList<>()).add(region);
//        }
//
//        // 2. 仅检查相邻网格的区域
//        List<int[]> mergedRegions = new ArrayList<>();
//        boolean[] merged = new boolean[regions.size()];
//        for (int i = 0; i < regions.size(); i++) {
//            if (merged[i]) continue;
//            int[] current = regions.get(i);
//            int[] mergedArea = current.clone();
//
//            // 3. 获取当前区域所在的网格和相邻网格
//            int gridX = current[0] / gridSize;
//            int gridY = current[1] / gridSize;
//            for (int dx = -1; dx <= 1; dx++) {
//                for (int dy = -1; dy <= 1; dy++) {
//                    String key = (gridX + dx) + "," + (gridY + dy);
//                    List<int[]> candidates = gridMap.getOrDefault(key, new ArrayList<>());
//                    for (int j = 0; j < candidates.size(); j++) {
//                        int[] other = candidates.get(j);
//                        if (merged[j] || !shouldMerge(mergedArea, other, mergeThreshold)) continue;
//
//                        // 4. 快速合并区域边界
//                        mergedArea[0] = Math.min(mergedArea[0], other[0]);
//                        mergedArea[1] = Math.min(mergedArea[1], other[1]);
//                        mergedArea[2] = Math.max(mergedArea[2], other[2]);
//                        mergedArea[3] = Math.max(mergedArea[3], other[3]);
//                        merged[j] = true;
//                    }
//                }
//            }
//            mergedRegions.add(mergedArea);
//        }
//
//        // 5. 过滤尺寸过大的区域
//        List<int[]> finalRegions = new ArrayList<>();
//        for (int[] region : mergedRegions) {
//            if ((region[2] - region[0] <= maxSize) && (region[3] - region[1] <= maxSize)) {
//                finalRegions.add(region);
//            } else {
//                // 对超限区域进行二次分割（可选）
//                finalRegions.add(region);
//            }
//        }
//
//        return finalRegions;
//    }
//    /** 快速区域合并判断（提前短路无效比较） */
//    private boolean shouldMerge(int[] a, int[] b, int threshold) {
//        // 快速失败条件
//        if (a[2] + threshold < b[0] || b[2] + threshold < a[0]) return false;
//        if (a[3] + threshold < b[1] || b[3] + threshold < a[1]) return false;
//
//        // 精确计算最小间距
//        int xGap = Math.max(0, Math.max(a[0] - b[2], b[0] - a[2]));
//        int yGap = Math.max(0, Math.max(a[1] - b[3], b[1] - a[3]));
//        return (xGap < threshold) && (yGap < threshold);
//    }
//
//    /** 并查集数据结构（路径压缩优化） */
//    private static class UnionFind {
//        private final int[] parent;
//
//        public UnionFind(int size) {
//            parent = new int[size];
//            for (int i = 0; i < size; i++) {
//                parent[i] = i;
//            }
//        }
//
//        public int find(int x) {
//            if (parent[x] != x) {
//                parent[x] = find(parent[x]); // 路径压缩
//            }
//            return parent[x];
//        }
//
//        public void union(int x, int y) {
//            int rootX = find(x);
//            int rootY = find(y);
//            if (rootX != rootY) {
//                parent[rootY] = rootX;
//            }
//        }
//    }
//
//    //========================================================
//    @Override
//    public void setImageMatrix(Matrix matrix) {
//        super.setImageMatrix(matrix);
//        // 记录变换矩阵及其逆矩阵
//        mTransformMatrix.set(matrix);
//        matrix.invert(mInverseMatrix);
//    }
//
//   /**
//     * 释放资源
//     */
//    public void recycle() {
//        if (mMaskBitmap != null && !mMaskBitmap.isRecycled()) {
//            mMaskBitmap.recycle();
//            mMaskBitmap = null;
//        }
//        if (mOriginalBitmap != null && !mOriginalBitmap.isRecycled()) {
//            mOriginalBitmap.recycle();
//            mOriginalBitmap = null;
//        }
//    }
//}

package com.dsq.rebackground.utils;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageView;

import java.util.*;
import java.util.concurrent.*;

public class MaskDrawView extends ImageView {
    private Bitmap mOriginalBitmap;   // 原始图片
    private Bitmap mMaskBitmap;       // 掩码图层
    private Canvas mMaskCanvas;       // 掩码画布

    private Path mCurrentPath;        // 当前绘制路径
    private Paint mPaint;             // 画笔配置
    private Matrix mTransformMatrix = new Matrix();  // 图片变换矩阵
    private Matrix mInverseMatrix = new Matrix();    // 逆矩阵（用于坐标转换）
    private List<int[]> mWatermarkRegions;
    private int mBrushColor = 0xFFFF0000; // 不透明红色（掩码用）
    private int mDisplayColor = 0xffff0000; // 半透明红色（显示用，Alpha=0.4）
    private int mBrushSize = 20;         // 画笔大小
    private boolean mIsPainting = true;  // 模式（true: 画笔，false: 橡皮擦）

    public MaskDrawView(Context context) {
        super(context);
        init();
    }

    public MaskDrawView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 初始化画笔
        mPaint = new Paint();
        mPaint.setAntiAlias(true);  // 启用抗锯齿
        mPaint.setStyle(Paint.Style.STROKE);  // 设置绘制样式为描边
        mPaint.setStrokeJoin(Paint.Join.ROUND);  // 设置连接点为圆角
        mPaint.setStrokeCap(Paint.Cap.ROUND);  // 设置端点为圆角
        mPaint.setColor(mDisplayColor);  // 设置显示颜色
//        mPaint.setAlpha(102); // 设置透明度为0.4（102/255≈0.4）
        mPaint.setStrokeWidth(mBrushSize);

        mCurrentPath = new Path();
    }

    /**
     * 设置原始图片并初始化掩码
     */
    public void setOriginalBitmap(Bitmap originalBitmap) {
        if (originalBitmap == null) return;
        this.mOriginalBitmap = originalBitmap;

        // 初始化掩码（与原始图片同尺寸）
        mMaskBitmap = Bitmap.createBitmap(
                originalBitmap.getWidth(),
                originalBitmap.getHeight(),
                Bitmap.Config.ARGB_8888
        );
        mMaskCanvas = new Canvas(mMaskBitmap);

        // 显示原始图片
        setImageBitmap(originalBitmap);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setupFitCenterMatrix(); // 视图尺寸变化时重新计算矩阵
    }

    /**
     * 计算并应用fitCenter模式的变换矩阵
     */
    private void setupFitCenterMatrix() {
        if (mOriginalBitmap == null || getWidth() == 0 || getHeight() == 0) return;

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int bitmapWidth = mOriginalBitmap.getWidth();
        int bitmapHeight = mOriginalBitmap.getHeight();

        Matrix matrix = new Matrix();
        float scale = Math.min(
                (float) viewWidth / bitmapWidth,
                (float) viewHeight / bitmapHeight
        );

        matrix.postScale(scale, scale);
        // 居中平移
        float dx = (viewWidth - bitmapWidth * scale) / 2;
        float dy = (viewHeight - bitmapHeight * scale) / 2;
        matrix.postTranslate(dx, dy);

        // 应用矩阵并更新逆矩阵
        setImageMatrix(matrix);
        mTransformMatrix.set(matrix);
        matrix.invert(mInverseMatrix);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. 绘制原始图片（父类 ImageView 已处理）
        // 2. 绘制掩码层
        if (mMaskBitmap != null && !mMaskBitmap.isRecycled()) {
            canvas.drawBitmap(mMaskBitmap, mTransformMatrix, null);
        }
    }

    /**
     * 获取最终掩码（供模型使用）
     */
    public Bitmap getMaskBitmap() {
        if (mMaskBitmap == null || mMaskBitmap.isRecycled()) {
            return null;
        }
        return mMaskBitmap.copy(Bitmap.Config.ARGB_8888, false);
    }

    public void clearMask() {
        clearWatermarkRegions();

        if (mOriginalBitmap != null && !mOriginalBitmap.isRecycled()) {
            // 重新创建掩码位图和画布（确保像素完全透明）
            mMaskBitmap = Bitmap.createBitmap(
                    mOriginalBitmap.getWidth(),
                    mOriginalBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            mMaskCanvas = new Canvas(mMaskBitmap);
            mMaskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); // 确保所有像素透明

            // 刷新视图
            invalidate();

            // 显示原始图片
            setImageBitmap(mOriginalBitmap);
        }
    }

    /**
     * 设置画笔大小
     */
    public void setBrushSize(int size) {
        mBrushSize = size;
        mPaint.setStrokeWidth(size);
    }

    /**
     * 设置画笔颜色
     */
    public void setBrushColor(int color) {
        // 保留红色通道，强制不透明用于检测
        mBrushColor = 0xFFFF0000;
        // 显示颜色设置透明度
        mDisplayColor = (color & 0x00FFFFFF) | 0x19000000;
        mPaint.setColor(mDisplayColor);
    }

    /**
     * 设置模式（画笔/橡皮擦）
     */
    public void setPaintingMode(boolean isPainting) {
        mIsPainting = isPainting;
        if (mIsPainting) {
            mPaint.setXfermode(null); // 画笔模式
        } else {
            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); // 橡皮擦模式
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mOriginalBitmap == null) return false;

        // 获取触摸点坐标（已考虑图片缩放和平移）
        float[] point = {event.getX(), event.getY()};
        mInverseMatrix.mapPoints(point);
        float x = point[0];
        float y = point[1];

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentPath.reset();  // 重置路径，开始新的绘制
                mCurrentPath.moveTo(x, y);  // 移动到触摸点位置
                invalidate();  // 确保触摸开始时立即刷新界面
                break;
            case MotionEvent.ACTION_MOVE:
                mMaskCanvas.drawPath(mCurrentPath, mPaint);
                mCurrentPath.lineTo(x, y);  // 继续绘制路径
                invalidate();  // 立即刷新界面，显示当前路径
                break;
            case MotionEvent.ACTION_UP:
                // 将路径绘制到掩码图层
                mMaskCanvas.drawPath(mCurrentPath, mPaint);
                mCurrentPath.reset();  // 重置路径，准备下一次绘制
                invalidate();  // 刷新视图，显示更新后的掩码
                break;
        }
        return true;
    }

    //=========================================
    /** 核心优化：高性能水印区域检测 */

    public void clearWatermarkRegions() {
        if (mWatermarkRegions != null) {
            mWatermarkRegions.clear();
        } else {
            mWatermarkRegions = new ArrayList<>();
        }
        Log.d("清理功能：", "水印区列表已清空");
    }

    public List<int[]> getWatermarkRegions() {
        if (mMaskBitmap == null) return new ArrayList<>();

        // 每次调用时清空并重新计算
        clearWatermarkRegions(); // 清空缓存
        mWatermarkRegions = new ArrayList<>(); // 初始化新列表

        List<int[]> rawAreas = getConnectedAreas();
        int maxDimension = Math.max(mMaskBitmap.getWidth(), mMaskBitmap.getHeight());
        if (maxDimension <= 400) {
            mWatermarkRegions.add(new int[]{0, 0, mMaskBitmap.getWidth(), mMaskBitmap.getHeight()});
        } else {
            mWatermarkRegions = mergeRegionsWithSpatialGrid(rawAreas, 110, 400);
        }

        return mWatermarkRegions;
    }

    private List<int[]> getConnectedAreas() {
        int width = mMaskBitmap.getWidth();
        int height = mMaskBitmap.getHeight();
        int[] pixels = new int[width * height];
        mMaskBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int[] labels = new int[width * height];
        Arrays.fill(labels, -1);
        UnionFind uf = new UnionFind(width * height);
        int currentLabel = 0; // 修复：在此处初始化currentLabel

        // First pass: 标记连通区域
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                // 忽略Alpha通道，只检测红色通道
                if ((pixel & 0x00ff0000) != 0x00ff0000) { // 检查红色通道是否达到最大值
                    continue;
                }

                int[] neighbors = getNeighborLabels(x, y, width, labels);
                if (neighbors.length == 0) {
                    labels[y * width + x] = currentLabel++;
                } else {
                    int minLabel = neighbors[0];
                    for (int label : neighbors) {
                        if (label < minLabel) minLabel = label;
                    }
                    labels[y * width + x] = minLabel;
                    for (int label : neighbors) {
                        if (label != minLabel) uf.union(minLabel, label);
                    }
                }
            }
        }

        // Second pass: 合并等效标签并计算边界
        Map<Integer, int[]> regionMap = new HashMap<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (labels[idx] == -1) continue;

                int root = uf.find(labels[idx]);
                int[] bounds = regionMap.getOrDefault(root, new int[]{x, y, x, y});
                bounds[0] = Math.min(bounds[0], x);
                bounds[1] = Math.min(bounds[1], y);
                bounds[2] = Math.max(bounds[2], x);
                bounds[3] = Math.max(bounds[3], y);
                regionMap.put(root, bounds);
            }
        }

        return new ArrayList<>(regionMap.values());
    }

    /** 获取相邻像素标签（优化内存访问模式） */
    private int[] getNeighborLabels(int x, int y, int width, int[] labels) {
        int[] neighbors = new int[4]; // 左、上、右、下四个邻居
        int count = 0;

        // 左邻居
        if (x > 0 && labels[y * width + (x - 1)] != -1) {
            neighbors[count++] = labels[y * width + (x - 1)];
        }

        // 上邻居
        if (y > 0 && labels[(y - 1) * width + x] != -1) {
            neighbors[count++] = labels[(y - 1) * width + x];
        }

        // 右邻居
        if (x < width - 1 && labels[y * width + (x + 1)] != -1) {
            neighbors[count++] = labels[y * width + (x + 1)];
        }

        // 下邻居
        if (y < labels.length / width - 1 && labels[(y + 1) * width + x] != -1) {
            neighbors[count++] = labels[(y + 1) * width + x];
        }

        return Arrays.copyOf(neighbors, count);
    }

    /** 基于空间网格的区域合并（时间复杂度 O(n)） */
    private List<int[]> mergeRegionsWithSpatialGrid(List<int[]> regions, int mergeThreshold, int maxSize) {
        // 1. 构建空间网格索引（200x200像素网格）
        Map<String, List<int[]>> gridMap = new HashMap<>();
        int gridSize = 200;
        for (int[] region : regions) {
            int gridX = region[0] / gridSize;
            int gridY = region[1] / gridSize;
            String key = gridX + "," + gridY;
            gridMap.computeIfAbsent(key, k -> new ArrayList<>()).add(region);
        }

        // 2. 仅检查相邻网格的区域
        List<int[]> mergedRegions = new ArrayList<>();
        boolean[] merged = new boolean[regions.size()];
        for (int i = 0; i < regions.size(); i++) {
            if (merged[i]) continue;
            int[] current = regions.get(i);
            int[] mergedArea = current.clone();

            // 3. 获取当前区域所在的网格和相邻网格
            int gridX = current[0] / gridSize;
            int gridY = current[1] / gridSize;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    String key = (gridX + dx) + "," + (gridY + dy);
                    List<int[]> candidates = gridMap.getOrDefault(key, new ArrayList<>());
                    for (int j = 0; j < candidates.size(); j++) {
                        int[] other = candidates.get(j);
                        if (merged[j] || !shouldMerge(mergedArea, other, mergeThreshold)) continue;

                        // 4. 快速合并区域边界
                        mergedArea[0] = Math.min(mergedArea[0], other[0]);
                        mergedArea[1] = Math.min(mergedArea[1], other[1]);
                        mergedArea[2] = Math.max(mergedArea[2], other[2]);
                        mergedArea[3] = Math.max(mergedArea[3], other[3]);
                        merged[j] = true;
                    }
                }
            }
            mergedRegions.add(mergedArea);
        }

        // 5. 过滤尺寸过大的区域
        List<int[]> finalRegions = new ArrayList<>();
        for (int[] region : mergedRegions) {
            if ((region[2] - region[0] <= maxSize) && (region[3] - region[1] <= maxSize)) {
                finalRegions.add(region);
            } else {
                // 对超限区域进行二次分割（可选）
                finalRegions.add(region);
            }
        }

        return finalRegions;
    }
    /** 快速区域合并判断（提前短路无效比较） */
    private boolean shouldMerge(int[] a, int[] b, int threshold) {
        // 快速失败条件
        if (a[2] + threshold < b[0] || b[2] + threshold < a[0]) return false;
        if (a[3] + threshold < b[1] || b[3] + threshold < a[1]) return false;

        // 精确计算最小间距
        int xGap = Math.max(0, Math.max(a[0] - b[2], b[0] - a[2]));
        int yGap = Math.max(0, Math.max(a[1] - b[3], b[1] - a[3]));
        return (xGap < threshold) && (yGap < threshold);
    }

    /** 并查集数据结构（路径压缩优化） */
    private static class UnionFind {
        private final int[] parent;

        public UnionFind(int size) {
            parent = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        public int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]); // 路径压缩
            }
            return parent[x];
        }

        public void union(int x, int y) {
            int rootX = find(x);
            int rootY = find(y);
            if (rootX != rootY) {
                parent[rootY] = rootX;
            }
        }
    }

    //========================================================
    @Override
    public void setImageMatrix(Matrix matrix) {
        super.setImageMatrix(matrix);
        // 记录变换矩阵及其逆矩阵
        mTransformMatrix.set(matrix);
        matrix.invert(mInverseMatrix);
    }

    /**
     * 释放资源
     */
    public void recycle() {
        if (mMaskBitmap != null && !mMaskBitmap.isRecycled()) {
            mMaskBitmap.recycle();
            mMaskBitmap = null;
        }
        if (mOriginalBitmap != null && !mOriginalBitmap.isRecycled()) {
            mOriginalBitmap.recycle();
            mOriginalBitmap = null;
        }
    }
}