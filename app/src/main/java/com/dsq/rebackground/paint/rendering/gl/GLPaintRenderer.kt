// ============================================================
// [MOD 2026-09-10 v3] 分层架构：stroke FBO + pigment FBO
//
// 修复历史（务必阅读，勿回退）：
//
//   v1（阶段 2 初版）：
//     - brush_stamp.frag 改为吸收系数增量 (1-C)
//     - blend 改为 glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
//     - pigment_composite.frag 改为 Beer-Lambert
//     - uAbsorption = 2.0
//     遗留问题：
//       * 笔迹中心三通道饱和 = (34,34,34) ≈ 细黑线
//       * 单笔绿 #B0E55D 输出 (40,123,35) 偏深
//       * 红+蓝=蓝（8-bit FBO clamp）
//
//   v2（帧内 stamp 合并 + -log 公式）：
//     - brush_stamp.frag 公式改为 absorption = -log(C) / uAbsorption
//     - 新增 stampAccumFBO，笔内 GL_MAX 合并
//     遗留问题：
//       * 跨帧累加 → 深色圆团
//       * 8-bit FBO 仍饱和 → 中心黑线、红+蓝=蓝
//
//   v3（分层架构，当前版本）：
//     - 引入 strokeFBO，笔内累积（GL_MAX，笔迹均匀）
//     - 笔结束时 flush 到 pigment（GL_ONE/GL_ONE 累加，允许混色）
//     - composite 时 pigment + stroke 相加，Beer-Lambert 输出
//     - uAbsorption 当前为 3.0；-log 公式保持单笔颜色不变并改善交叠色。
//     - 笔生命周期由 ACTION_UP / ACTION_CANCEL 离散事件驱动。
//
//   v3 + v4frag（当前）：
//     - brush_stamp.frag 改为非预乘 (absorption, alpha)
//     - pigment_composite.frag 读 stroke 时乘 stroke.a
//     目的：消除 GL_MAX 对 RGB/A 独立作用导致的对角白线
//
// 三个目标同时达成：
//   1. 中心黑线消失：笔内 GL_MAX 不累加
//   2. 红+蓝=紫：抬起时 GL_ONE/GL_ONE 累加，吸收系数叠加
//   3. 重叠变深可控：每笔独立 stroke，抬笔才累积
//
// 请勿回退。
// ============================================================

// ============================================================
// [DEBUG PROBE] 探针工具说明（由调试协作流程添加）
//
// 本文件包含 PIGMENT_PROBE 量化探针，用于替代肉眼判断：
//   1. probeRegion()   —— 读取指定 FBO 某中心区域的平均 RGBA
//   2. probeTotalMass() —— 降采样估计 pigment FBO 的整体颜料质量
//
// 规则：
//   - 探针只在 DEBUG_PROBE = true 时执行，默认 true（当前调试中）
//   - 探针不修改任何渲染逻辑，只读取 FBO 并打印日志
//   - 探针函数以 probe 开头，日志 tag = "PIGMENT_PROBE"
//   - 探针在读取前会保存当前 FBO 绑定，读取后恢复
//   - 探针位置会自动跟随最近一次 brush stamp 的中心（lastStampCenterX/Y）
//   - 探针只在有新 stamp 写入的帧执行，避免静止时刷屏
//   - 探针已修正 Y 轴翻转和 R/B 字节顺序（glReadPixels 小端打包）
//   - 修改本文件时请勿删除探针函数及其调用点
//
// 关键坑（已修复，勿回退）：
//   A. glReadPixels 是左下角原点 → 读取前需要 Y 翻转：
//        glCy = documentHeight - cy
//   B. glReadPixels 返回的字节流是 [R,G,B,A]，但 Android 小端下
//      用 asIntBuffer 读出的 int = 0xAABBGGRR，
//      即 A 在高字节、R 在最低字节。
//      正确解析：
//        a = (p ushr 24) and 0xFF
//        b = (p ushr 16) and 0xFF
//        g = (p ushr 8)  and 0xFF
//        r = p and 0xFF
//      之前的代码把 r 和 b 反了，导致红色被读成蓝色。
// ============================================================
package com.dsq.rebackground.paint.rendering.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import com.dsq.rebackground.paint.brush.BrushStamp
import com.dsq.rebackground.paint.rendering.PaintRenderer
import com.dsq.rebackground.paint.rendering.RenderGraph
import com.dsq.rebackground.paint.rendering.RenderTarget
import com.dsq.rebackground.paint.pigment.VelocityField
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GLPaintRenderer(
    private val renderGraph: RenderGraph = RenderGraph(emptyList()),
    private val clearRed: Float = 1f,
    private val clearGreen: Float = 1f,
    private val clearBlue: Float = 1f,
    private val clearAlpha: Float = 1f,
    private val brushStampVertexSource: String? = null,
    private val brushStampFragmentSource: String? = null,
    private val presentVertexSource: String? = null,
    private val presentFragmentSource: String? = null,
    private val fullscreenVertexSource: String? = null,
    private val velocityFragmentSource: String? = null,
    private val pigmentDiffuseFragmentSource: String? = null,
    private val pigmentCompositeFragmentSource: String? = null,
    private val pigmentAddFragmentSource: String? = null,
    private val mixboxLutBitmap: Bitmap? = null,
    private val paperCompositeFragmentSource: String? = null,
    private val particleVertexSource: String? = null,
    private val particleFragmentSource: String? = null,
    // [MOD PR-2.4] SDF 胶囊着色器
    private val segmentSdfVertexSource: String? = null,
    private val segmentSdfFragmentSource: String? = null
) : PaintRenderer, GLSurfaceView.Renderer {
    init {
        require(clearRed.isFinite() && clearRed in 0f..1f)
        require(clearGreen.isFinite() && clearGreen in 0f..1f)
        require(clearBlue.isFinite() && clearBlue in 0f..1f)
        require(clearAlpha.isFinite() && clearAlpha in 0f..1f)
    }

    private val COLOR_TAG = "COLOR_TRACE"

    // ============================================================
    // [DEBUG PROBE] 探针开关与常量
    // 定位完问题后请把 DEBUG_PROBE 改为 false，避免影响性能
    // ============================================================
    private val PROBE_TAG = "PIGMENT_PROBE"
    private val DEBUG_PROBE = false

    // ============================================================
    // [MOD 2026-09-10 v3] 全局 Beer-Lambert 吸收斜率
    // v1/v2 用 2.0，单笔 abs = 1.0，8-bit 立刻饱和
    // 阶段 2 收尾改为 3.0，避免红蓝交叠的 G 通道过暗。
    // -log 公式确保单笔结果不随此系数变化。
    // ============================================================
    private val U_ABSORPTION = 3.0f
    @Volatile
    private var currentStrokeFlow: Float = 1f

    // ============================================================
    // [MOD PR-2.4] SDF 胶囊参数
    // ============================================================
    /** 直径小于该值走 SDF 胶囊路径（文档像素） */
    private val SDF_THRESHOLD_PX = 4.0f
    // [MOD PR-2.7] SDF 过渡带 1.0 像素（顶级软件经验值）
    private val SDF_AA_PX = 1.0f
    // ============================================================
    // ============================================================

    val context = GLContext()
    private val stateCache = GLStateCache()
    private val pendingStamps = AtomicReference<List<ColoredBrushStamp>>(emptyList())
    private val pendingDocumentSize = AtomicReference<Pair<Int, Int>?>(null)
    private val endStrokeRequested = AtomicBoolean(false)
    // [MOD PR-2.6] cancel 请求：丢弃当前笔，不 flush 到 pigment
    private val cancelStrokeRequested = AtomicBoolean(false)
    private val clearRequested = AtomicBoolean(false)
    @Volatile private var pendingBrushTextures: Map<String, Bitmap>? = null
    private val brushTextureHandles = HashMap<String, Int>()
    private val brushTextureSizes = HashMap<String, Pair<Int, Int>>()
    // ============================================================
    // [MOD PR-2.7] 纹理 alpha 均值缓存（key = texturePath）
    //   用于 brush_stamp.frag 归一化：纹理 = 质感调制
    // ============================================================
    private val brushTextureAlphaMeans = HashMap<String, Float>()
    // ============================================================
    private var brushStampShader: GLShader? = null

    // [MOD PR-2.4] SDF 胶囊资源
    private var segmentSdfShader: GLShader? = null
    private var segmentSdfBuffer: GLBuffer? = null


    private var brushStampBuffer: GLBuffer? = null
    private var canvasTexture: GLTexture? = null
    private var canvasFramebuffer: GLFramebuffer? = null
    private var pigmentTexture: GLTexture? = null
    private var pigmentFramebuffer: GLFramebuffer? = null
    private var compositeTexture: GLTexture? = null
    private var compositeFramebuffer: GLFramebuffer? = null
    private var diffuseTexture: GLTexture? = null
    private var diffuseFramebuffer: GLFramebuffer? = null

    // ============================================================
    // [MOD 2026-09-10 v3] stroke FBO：本笔累积
    // 语义：RGB = 本笔每像素最大吸收系数（GL_MAX 合并）
    //       A  = 本笔每像素最大覆盖
    // 笔开始时清空，笔结束时一次性 flush 到 pigmentFBO
    // ============================================================
    private var strokeTexture: GLTexture? = null
    private var strokeFramebuffer: GLFramebuffer? = null
    private var pigmentAddShader: GLShader? = null
    // ============================================================

    // ============================================================
    // [MOD 2026-09-11] Mixbox 集成
    //   - mixboxLutHandle：LUT 纹理句柄（从 bitmap 加载）
    //   - pigmentPingPongTexture/Framebuffer：ping-pong 用于 Mixbox pass
    // ============================================================
    private var mixboxLutHandle: Int = 0
    private var pigmentPingPongTexture: GLTexture? = null
    private var pigmentPingPongFramebuffer: GLFramebuffer? = null
    // ============================================================

    private var presentShader: GLShader? = null
    private var presentBuffer: GLBuffer? = null
    private var fullscreenBuffer: GLBuffer? = null
    private var pigmentDiffuseShader: GLShader? = null
    private var pigmentCompositeShader: GLShader? = null
    private var velocityShader: GLShader? = null
    private var velocityField: VelocityField? = null
    private var documentWidth = 0
    private var documentHeight = 0
    private var diffuseFramesRemaining = 0

    // ============================================================
    // [MOD 2026-09-10 v3] 笔生命周期状态
    // 由 ACTION_UP / ACTION_CANCEL 通过 endStrokeRequested 结束笔画。
    // ============================================================
    private var strokeActive = false
    // ============================================================
    // [MOD PR-2.3] GL surface 就绪标志
    //   - onSurfaceChanged 末尾设为 true
    //   - markSurfaceLost() 设为 false（由 PaintGLSurfaceView.onPause 调用）
    //   captureBitmap 检查此标志，避免在 GL context 无效时读取 FBO
    // ============================================================

    private var shouldScanPurple = false
    @Volatile private var lastFrameTimeMs = 0f
    @Volatile private var lastRenderGlError = GLES20.GL_NO_ERROR
    private var observedFrameGlError = GLES20.GL_NO_ERROR
    // ============================================================

    // ============================================================
    // [DEBUG PROBE] 记录最近一次 brush stamp 的中心，供探针自动跟随
    // 初始为 -1，表示还没有任何笔迹，探针不执行
    // ============================================================
    private var lastStampCenterX = -1
    private var lastStampCenterY = -1
    // [MOD PR-2.1] 背景图（图片创建画布）
    @Volatile private var pendingBackgroundBitmap: Bitmap? = null
    @Volatile private var brushRed = 0.96f
    // ============================================================
    // [MOD PR-2.3] surface 就绪标志
    //   - onSurfaceChanged 末尾设 true
    //   - markSurfaceLost() 设 false（由 PaintGLSurfaceView.onPause 调用）
    //   - captureBitmap 检查此标志，避免在 GL context 无效时读 FBO
    // ============================================================
    @Volatile
    var isSurfaceReady: Boolean = false
        private set
    // ============================================================
    @Volatile private var brushGreen = 0.96f
    @Volatile private var brushBlue = 0.96f
    @Volatile private var brushAlpha = 1f
    // [MOD 2026-09-11] 笔内覆盖度上限（0~1）
    @Volatile private var strokeOpacityLimit = 1f
    @Volatile private var dryPaperHeightAmplitude = 0.5f
    @Volatile private var dryPaperGrainScale = 1f
    @Volatile private var dryPaperSeed = 0
    @Volatile private var viewScale = 1f
    @Volatile private var viewOffsetX = 0f
    @Volatile private var viewOffsetY = 0f
    @Volatile private var viewRotation = 0f

    // ======================= 调试日志辅助 =======================
    private fun logGlState(tag: String) {
        val vp = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
        val fbo = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, fbo, 0)
        val err = GLES20.glGetError()
        if (err != GLES20.GL_NO_ERROR && observedFrameGlError == GLES20.GL_NO_ERROR) {
            observedFrameGlError = err
        }
        Log.d("GLPaintRenderer",
            "[$tag] viewport=[${vp[0]},${vp[1]} ${vp[2]}x${vp[3]}] fbo=${fbo[0]} " +
                    "doc=${documentWidth}x${documentHeight} ctx=${context.width}x${context.height} " +
                    "viewScale=$viewScale viewOff=($viewOffsetX,$viewOffsetY) err=$err")
    }
    // ============================================================

    // ============================================================
    // [DEBUG PROBE] 读取指定 FBO 某中心区域的平均 RGBA
    // 参数：
    //   fbo     —— 目标 FBO（pigmentFramebuffer / compositeFramebuffer / strokeFramebuffer）
    //   cx, cy  —— 读取中心坐标（文档坐标系，左上角原点）
    //   halfW/H —— 采样半宽/半高，默认 4 → 读 9x9 区域
    //   tag     —— 日志标记，用于区分调用点
    // 行为：
    //   - 保存当前 FBO 绑定 → 绑定目标 FBO → glReadPixels → 恢复绑定
    //   - 不修改任何渲染状态，不抛出异常影响主流程
    // 关键修复：
    //   - Y 轴翻转：glReadPixels 是左下角原点
    //   - R/B 顺序：Android 小端下 int=0xAABBGGRR
    // ============================================================
    private fun probeRegion(
        fbo: GLFramebuffer?,
        cx: Int,
        cy: Int,
        halfW: Int = 4,
        halfH: Int = 4,
        tag: String,
        highPrecision: Boolean = false
    ) {
        if (!DEBUG_PROBE) return
        if (fbo == null) {
            Log.d(PROBE_TAG, "[$tag] fbo is null, skip")
            return
        }
        if (documentWidth <= 0 || documentHeight <= 0) return

        val w = halfW * 2 + 1
        val h = halfH * 2 + 1
        if (w > documentWidth || h > documentHeight) {
            Log.d(PROBE_TAG, "[$tag] document too small for ${w}x${h}")
            return
        }
        val x = (cx - halfW).coerceIn(0, documentWidth - w)
        // ============================================================
        // [DEBUG PROBE] Y 轴翻转
        // 文档坐标是左上角原点，glReadPixels 是左下角原点
        // ============================================================
        val glCy = documentHeight - cy
        val y = (glCy - halfH).coerceIn(0, documentHeight - h)

        val prevFbo = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)

        try {
            stateCache.bindFramebuffer(fbo.handle)
            if (highPrecision) {
                val n = w * h
                val buf = ByteBuffer.allocateDirect(n * 4 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                GLES20.glReadPixels(x, y, w, h,
                    GLES20.GL_RGBA, GLES20.GL_FLOAT, buf)
                buf.rewind()
                val flt = FloatArray(n * 4); buf.get(flt)
                var r = 0.0; var g = 0.0; var b = 0.0; var a = 0.0
                for (i in 0 until n) {
                    r += flt[i * 4]
                    g += flt[i * 4 + 1]
                    b += flt[i * 4 + 2]
                    a += flt[i * 4 + 3]
                }
                Log.d(PROBE_TAG,
                    "[$tag] region=${w}x${h} at=($x,$y) " +
                            "avg RGBA=(${(r / n * 255).toInt()}, ${(g / n * 255).toInt()}, " +
                            "${(b / n * 255).toInt()}, ${(a / n * 255).toInt()})")
            } else {
            val buf = ByteBuffer.allocateDirect(w * h * 4)
                .order(ByteOrder.nativeOrder()).asIntBuffer()
            GLES20.glReadPixels(x, y, w, h,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            val pix = IntArray(w * h); buf.get(pix)

            var r = 0L; var g = 0L; var b = 0L; var a = 0L
            for (p in pix) {
                // ============================================================
                // [DEBUG PROBE] 正确解析通道
                // Android 小端下 glReadPixels 返回的 int = 0xAABBGGRR
                //   A 在高字节，R 在最低字节
                // ============================================================
                a += (p ushr 24) and 0xFF
                b += (p ushr 16) and 0xFF
                g += (p ushr 8)  and 0xFF
                r += p and 0xFF
            }
            val n = pix.size
            Log.d(PROBE_TAG,
                "[$tag] region=${w}x$h at=($x,$y) " +
                        "avg RGBA=(${r / n}, ${g / n}, ${b / n}, ${a / n})")
            }
        } catch (e: Exception) {
            Log.e(PROBE_TAG, "[$tag] glReadPixels failed", e)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
            stateCache.invalidate()
        }
    }

    // ============================================================
    // [MOD PR-2.7] 计算纹理 alpha 均值（忽略近透明像素）
    //   用于归一化：让笔迹整体达到 1.0 浓度
    // ============================================================
    private fun computeAlphaMean(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0.5f
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var sum = 0.0
        var count = 0
        for (p in pixels) {
            val a = (p ushr 24) and 0xFF
            val alpha = a / 255f
            // 忽略近透明像素（避免分母被大量 0 拉低）
            if (alpha > 0.05f) {
                sum += alpha
                count++
            }
        }
        val mean = if (count > 0) (sum / count).toFloat() else 0.5f
        Log.d("GLPaintRenderer",
            "computeAlphaMean: ${w}x$h mean=$mean count=$count/${pixels.size}")
        return mean
    }
    // ============================================================

    // ============================================================
    // [MOD 2026-09-11] 通用颜色扫描
    //
    // 用途：诊断"红蓝交叠"失败在哪一层
    // 原理：遍历 FBO 全屏像素，统计满足 predicate 的像素数和平均色
    //
    // predicate 参数：(r, g, b) → Boolean
    //   - r、g、b 均为 0~255 的 Int
    //   - 返回 true 的像素会被统计
    //
    // 与旧 scanPurplePixels 的区别：
    //   - 旧版硬编码"紫色"判定，只能扫 composite
    //   - 新版通过 predicate 自定义判定，可扫 pigment/stroke/composite 任意层
    // ============================================================
    private fun scanColorPixels(
        fbo: GLFramebuffer?,
        tag: String,
        predicate: (r: Int, g: Int, b: Int) -> Boolean
    ) {
        if (!DEBUG_PROBE) return
        if (fbo == null) {
            Log.d(PROBE_TAG, "[$tag] fbo is null, skip")
            return
        }
        if (documentWidth <= 0 || documentHeight <= 0) return

        val prevFbo = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)

        try {
            stateCache.bindFramebuffer(fbo.handle)
            val total = documentWidth * documentHeight
            val buf = ByteBuffer.allocateDirect(total * 4)
                .order(ByteOrder.nativeOrder()).asIntBuffer()
            GLES20.glReadPixels(0, 0, documentWidth, documentHeight,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            val pix = IntArray(total); buf.get(pix)

            var count = 0L
            var sumR = 0L; var sumG = 0L; var sumB = 0L
            var minX = documentWidth; var maxX = 0
            var minY = documentHeight; var maxY = 0
            val step = 4

            var y = 0
            while (y < documentHeight) {
                var x = 0
                while (x < documentWidth) {
                    val p = pix[y * documentWidth + x]
                    // [DEBUG PROBE] Android 小端 int = 0xAABBGGRR
                    val r = p and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = (p ushr 16) and 0xFF

                    if (predicate(r, g, b)) {
                        count++
                        sumR += r; sumG += g; sumB += b
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                    x += step
                }
                y += step
            }

            if (count > 0) {
                Log.d(PROBE_TAG,
                    "[$tag] count=$count " +
                            "avg=(${sumR / count}, ${sumG / count}, ${sumB / count}) " +
                            "bbox=(${minX},${documentHeight - maxY})-(${maxX},${documentHeight - minY})")
            } else {
                Log.d(PROBE_TAG, "[$tag] count=0")
            }
        } catch (e: Exception) {
            Log.e(PROBE_TAG, "[$tag] scan failed", e)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
            stateCache.invalidate()
        }
    }

    // ============================================================
    // [DEBUG PROBE] 估计 pigment FBO 的整体颜料质量
    // 方法：按 step 降采样读整张 FBO 中心区域，累加 (RGB 均值 × alpha)
    // 用途：判断扩散是否在破坏总质量守恒
    // 关键修复：R/B 通道顺序同 probeRegion
    // ============================================================
    private fun probeTotalMass(fbo: GLFramebuffer?, tag: String) {
        if (!DEBUG_PROBE) return
        if (fbo == null) return
        if (documentWidth <= 0 || documentHeight <= 0) return

        val stepX = (documentWidth / 64).coerceAtLeast(1)
        val stepY = (documentHeight / 64).coerceAtLeast(1)
        val w = (documentWidth / stepX).coerceAtLeast(1)
        val h = (documentHeight / stepY).coerceAtLeast(1)

        val prevFbo = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFbo, 0)

        try {
            stateCache.bindFramebuffer(fbo.handle)
            // ============================================================
            // [DEBUG PROBE] 读中心区域而不是左下角，更能代表整体
            // ============================================================
            val x0 = ((documentWidth - w) / 2).coerceAtLeast(0)
            val y0 = ((documentHeight - h) / 2).coerceAtLeast(0)
            val buf = ByteBuffer.allocateDirect(w * h * 4)
                .order(ByteOrder.nativeOrder()).asIntBuffer()
            GLES20.glReadPixels(x0, y0, w, h,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            val pix = IntArray(w * h); buf.get(pix)

            var sum = 0.0
            for (p in pix) {
                // ============================================================
                // [DEBUG PROBE] 正确解析通道（同 probeRegion）
                // ============================================================
                val a = (p ushr 24) and 0xFF
                val b = (p ushr 16) and 0xFF
                val g = (p ushr 8)  and 0xFF
                val r = p and 0xFF
                sum += ((r + g + b) / 3.0) * (a / 255.0)
            }
            Log.d(PROBE_TAG,
                "[$tag] totalMass=%.0f (sampled ${w}x${h} step=${stepX}x$stepY)".format(sum))
        } catch (e: Exception) {
            Log.e(PROBE_TAG, "[$tag] glReadPixels failed", e)
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, prevFbo[0])
            stateCache.invalidate()
        }
    }
    // ============================================================

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        onSurfaceCreated()
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        renderFrame()
    }

    override fun onSurfaceCreated() {
        context.markCreated()
        stateCache.invalidate()
        GLES20.glClearColor(clearRed, clearGreen, clearBlue, clearAlpha)
        Log.d("GLPaintRenderer", "onSurfaceCreated GL_VERSION=" + GLES20.glGetString(GLES20.GL_VERSION) +
                " GL_RENDERER=" + GLES20.glGetString(GLES20.GL_RENDERER))
        if (brushStampVertexSource != null && brushStampFragmentSource != null) {
            brushStampShader = GLShader(context, brushStampVertexSource, brushStampFragmentSource).also { it.create() }
            brushStampBuffer = GLBuffer(context).also {
                it.upload(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            }
        }
        if (presentVertexSource != null && presentFragmentSource != null) {
            presentShader = GLShader(context, presentVertexSource, presentFragmentSource).also { it.create() }
            presentBuffer = GLBuffer(context).also {
                it.upload(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            }
        }
        if (fullscreenVertexSource != null) {
            fullscreenBuffer = GLBuffer(context).also {
                it.upload(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            }
            if (pigmentDiffuseFragmentSource != null) {
                pigmentDiffuseShader = GLShader(context, fullscreenVertexSource, pigmentDiffuseFragmentSource).also { it.create() }
            }
            if (pigmentCompositeFragmentSource != null) {
                pigmentCompositeShader = GLShader(context, fullscreenVertexSource, pigmentCompositeFragmentSource).also { it.create() }
            }
            // ============================================================
            // [MOD 2026-09-10 v3] stroke → pigment flush 用的 shader
            // ============================================================
            if (pigmentAddFragmentSource != null) {
                pigmentAddShader = GLShader(context, fullscreenVertexSource, pigmentAddFragmentSource).also { it.create() }
            }
            // ============================================================
            // [MOD 2026-09-11] 加载 Mixbox LUT 纹理
            // ============================================================
            val lutBitmap = mixboxLutBitmap
            if (lutBitmap != null) {
                mixboxLutHandle = createBrushTexture(lutBitmap)
                Log.d("GLPaintRenderer", "Mixbox LUT loaded, handle=$mixboxLutHandle size=${lutBitmap.width}x${lutBitmap.height}")
            } else {
                Log.w("GLPaintRenderer", "mixboxLutBitmap is null, Mixbox will not work")
            }
            // ============================================================
            if (velocityFragmentSource != null) {
                velocityShader = GLShader(context, fullscreenVertexSource, velocityFragmentSource).also { it.create() }
            }

            // ============================================================
            // [MOD PR-2.4] SDF 胶囊着色器初始化
            // ============================================================
            if (segmentSdfVertexSource != null && segmentSdfFragmentSource != null) {
                segmentSdfShader = GLShader(
                    context,
                    segmentSdfVertexSource,
                    segmentSdfFragmentSource
                ).also { it.create() }
                segmentSdfBuffer = GLBuffer(context)
                Log.d("GLPaintRenderer", "SDF capsule shader initialized")
            }
            // ============================================================
        }
        Log.d(
            "GLPaintRenderer",
            "onSurfaceCreated fullscreenBuffer=${fullscreenBuffer != null} " +
                    "diffuse=${pigmentDiffuseShader != null} composite=${pigmentCompositeShader != null} " +
                    "add=${pigmentAddShader != null}"
        )
        // [MOD PR-2.3] context 刚重建，stateCache 必须失效
        stateCache.invalidate()
    }

    // ============================================================
    // [MOD PR-2.3] 标记 surface 丢失
    //   在 PaintGLSurfaceView.onPause 里调用
    //   效果：captureBitmap 会等到 surface 重建后才执行
    // ============================================================
    fun markSurfaceLost() {
        isSurfaceReady = false
        stateCache.invalidate()
        Log.d("GLPaintRenderer", "markSurfaceLost: isSurfaceReady=false")
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        Log.d("GLPaintRenderer", "onSurfaceChanged width=$width height=$height")
        context.requireReady()
        context.resize(width, height)
        if (documentWidth == 0 || documentHeight == 0) {
            documentWidth = width
            documentHeight = height
        }

        // ============================================================
        // [MOD PR-2.2] surface 重建后 GL 状态全部重置，
        // 必须清除 stateCache 避免"以为是某状态但其实不是"的 bug。
        //
        // 症状：Settings 返回后 captureBitmap，日志 fbo=0，
        //       但 stateCache 认为已绑定 compositeFbo → 跳过 glBindFramebuffer
        //       → 渲染到屏幕 FBO → composite FBO 从未更新 → 导出空白。
        // ============================================================
        stateCache.invalidate()
        stateCache.viewport(width, height)
        logGlState("after onSurfaceChanged")
        // [MOD PR-2.3] surface 已就绪
        isSurfaceReady = true
    }

    // ============================================================
    // [MOD 2026-09-10 v3] 笔生命周期管理
    // ============================================================

    /**
     * 笔开始：
     *   - 清空 stroke FBO（丢弃上一笔残留）
     *   - strokeActive = true
     * 触发时机：检测到本帧有新 stamp 且 strokeActive == false
     */
    private fun beginStrokeInternal() {
        val strokeFbo = strokeFramebuffer ?: return
        stateCache.bindFramebuffer(strokeFbo.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        strokeActive = true
        Log.d("GLPaintRenderer", "beginStroke: stroke FBO cleared, strokeActive=true")
    }

    /**
     * 笔结束：
     *   - 把 stroke FBO 内容一次性 flush 到 pigment FBO（GL_ONE/GL_ONE 累加 RGB）
     *   - 清空 stroke FBO（为下一笔做准备）
     *   - strokeActive = false
     * 触发时机：触摸层发出的 ACTION_UP / ACTION_CANCEL 离散事件。
     */
    private fun endStrokeInternal() {
        val pigmentFbo = pigmentFramebuffer ?: return
        stateCache.bindFramebuffer(pigmentFbo.handle)
        stateCache.viewport(documentWidth, documentHeight)
        flushStrokeToPigment()
        Log.d("GLPaintRenderer", "endStroke: stroke flushed to pigment")

        val strokeFbo = strokeFramebuffer
        if (strokeFbo != null) {
            stateCache.bindFramebuffer(strokeFbo.handle)
            stateCache.viewport(documentWidth, documentHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        strokeActive = false
        // ============================================================
        // [MOD 2026-09-10] L2 防线：清空 pendingStamps
        // 丢弃 ACTION_UP 之前 UI 线程积压但未处理的残留点，
        // 防止跨帧延迟到下一笔被误画。
        // ============================================================
        pendingStamps.set(emptyList())
    }

    /**
     * stroke → pigment：把本笔累积的吸收系数累加到 pigment 历史
     *
     * 语义：
     *   stroke.rgb = 本笔每像素最大吸收（GL_MAX 合并的结果）
     *   stroke.a   = 本笔每像素最大覆盖
     *
     * Blend：
     *   RGB: GL_ONE / GL_ONE          → 吸收系数线性累加（红+蓝=紫的关键）
     *   A:   GL_ONE / GL_ONE_MINUS_SRC_ALPHA → 湿度标准 over
     *
     * 调用前提：
     *   - 已 stateCache.bindFramebuffer(pigmentFramebuffer.handle)
     *   - 已 stateCache.viewport(documentWidth, documentHeight)
     */
    private fun flushStrokeToPigment() {
        val shader = pigmentAddShader ?: return
        // ============================================================
        // [MOD PR-2.6-fix] 保留精简日志（验证后可删）
        Log.d("GLPaintRenderer",
            "flush: flow=$currentStrokeFlow opacity=$strokeOpacityLimit")
        // ============================================================
        val buffer = fullscreenBuffer ?: return
        val strokeTex = strokeTexture ?: return
        val oldPigmentTex = pigmentTexture ?: return
        val oldPigmentFbo = pigmentFramebuffer ?: return
        val newPigmentFbo = pigmentPingPongFramebuffer ?: return
        val newPigmentTex = pigmentPingPongTexture ?: return

        stateCache.bindFramebuffer(newPigmentFbo.handle)
        stateCache.viewport(documentWidth, documentHeight)

        GLES20.glUseProgram(shader.program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, strokeTex.handle)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "uStroke"), 0)

        // [MOD 2026-09-11] 传 uStrokeOpacity 覆盖度上限
        val opacityLoc = GLES20.glGetUniformLocation(shader.program, "uStrokeOpacity")
        if (opacityLoc != -1) {
            GLES20.glUniform1f(opacityLoc, strokeOpacityLimit)
        }

        // ============================================================
        // [MOD PR-2.6-fix] 传 uFlow
        //   之前这段误加在 compositePigmentOverCanvas 里（pigment_composite 无 uFlow）
        //   导致真正 flush 时 uFlow 保持默认值 0 → 笔迹被 pigment_add.frag 丢弃
        // ============================================================
        val flowLoc = GLES20.glGetUniformLocation(shader.program, "uFlow")
        if (flowLoc != -1) {
            GLES20.glUniform1f(flowLoc, currentStrokeFlow)
        } else {
            Log.w("GLPaintRenderer",
                "flushStrokeToPigment: uFlow location not found! " +
                        "Check pigment_add.frag has 'uniform float uFlow;'")
        }
        // ============================================================
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, oldPigmentTex.handle)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "uOldPigment"), 1)

        if (mixboxLutHandle != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mixboxLutHandle)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "mixbox_lut"), 2)
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.handle)
        val pos = GLES20.glGetAttribLocation(shader.program, "aUnitPosition")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)

        // 交换：新 pigment 变成当前，旧 pigment 的 FBO 变成 ping-pong
        pigmentTexture = newPigmentTex
        pigmentFramebuffer = newPigmentFbo
        pigmentPingPongTexture = oldPigmentTex
        pigmentPingPongFramebuffer = oldPigmentFbo

        Log.d("GLPaintRenderer", "flushStrokeToPigment: Mixbox swap complete")
        Log.d("GLPaintRenderer", "flush SWAP: old pigTex=${oldPigmentTex.handle} old pigFbo=${oldPigmentFbo.handle} " +
                "new pigTex=${newPigmentTex.handle} new pigFbo=${newPigmentFbo.handle} " +
                "现在 pigmentFramebuffer=${pigmentFramebuffer?.handle} pigmentTexture=${pigmentTexture?.handle}")
    }
    // ============================================================

    override fun renderFrame() {
        val frameStartNanos = System.nanoTime()
        observedFrameGlError = GLES20.GL_NO_ERROR
        context.requireReady()
        pendingDocumentSize.getAndSet(null)?.let { (width, height) ->
            documentWidth = width
            documentHeight = height
        }
        logGlState("renderFrame START")
        ensureCanvasResources()
        ensureOffscreenResources()
        uploadBrushTexturesIfNeeded()
        val framebuffer = canvasFramebuffer
        val pigmentFramebuffer = pigmentFramebuffer
        val compositeFramebuffer = compositeFramebuffer
        val strokeFramebuffer = strokeFramebuffer
        if (framebuffer == null || pigmentFramebuffer == null || compositeFramebuffer == null || strokeFramebuffer == null) {
            Log.d("GLPaintRenderer", "renderFrame missing FBO canvas=$framebuffer pigment=$pigmentFramebuffer " +
                    "composite=$compositeFramebuffer stroke=$strokeFramebuffer")
            clearRequested.set(false)
            stateCache.bindFramebuffer(0)
            if (context.width > 0 && context.height > 0) stateCache.viewport(context.width, context.height)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        } else {
            if (clearRequested.getAndSet(false)) {
                clearDocumentTargets(framebuffer, pigmentFramebuffer, compositeFramebuffer)
            }

            // ============================================================
            // [MOD PR-2.1] 应用背景图（必须在 clear 之后、笔迹之前）
            // ============================================================
            val pendingBg = pendingBackgroundBitmap
            if (pendingBg != null && documentWidth > 0 && documentHeight > 0) {
                blitBackgroundToCanvas(pendingBg)
                pendingBackgroundBitmap = null
            }

            // Cancel discards pending input. A normal end must consume the
            // pending batch first, then flush that same stroke. Doing this in
            // the opposite order loses fast DOWN/MOVE/UP sequences.
            val wasCancelStroke = cancelStrokeRequested.getAndSet(false)
            if (wasCancelStroke) {
                cancelStrokeInternal()
            }

            val wasEndStroke = endStrokeRequested.getAndSet(false)
            val batch = if (wasCancelStroke) {
                pendingStamps.set(emptyList())
                emptyList()
            } else {
                pendingStamps.getAndSet(emptyList())
            }
            // ============================================================
            // [DEBUG PROBE] hasNewStamp 用于控制本帧是否打探针
            // 静止时 batch 为空 → hasNewStamp=false → 探针不执行 → 日志不刷屏
            // ============================================================
            val hasNewStamp = batch.isNotEmpty()

            if (hasNewStamp) {
                if (!strokeActive) {
                    beginStrokeInternal()
                }
                // 把本帧 batch 用 GL_MAX 画到 stroke FBO
                stateCache.bindFramebuffer(strokeFramebuffer.handle)
                if (documentWidth > 0 && documentHeight > 0) stateCache.viewport(documentWidth, documentHeight)
                drawBrushStampBatchToStroke(batch)
            }
            if (wasEndStroke && strokeActive) {
                endStrokeInternal()
                shouldScanPurple = DEBUG_PROBE
            }
            // ============================================================

            // ============================================================
            // [DEBUG PROBE] 观察点 A：stroke + pigment
            // ============================================================
            if (hasNewStamp && lastStampCenterX >= 0) {
                probeRegion(strokeFramebuffer, lastStampCenterX, lastStampCenterY,
                    tag = "stroke afterDeposit", highPrecision = true)
                probeRegion(pigmentFramebuffer, lastStampCenterX, lastStampCenterY,
                    tag = "pigment afterDeposit", highPrecision = true)
            }

            // ============================================================
            // [MOD 2026-09-10 v3] 扩散：只作用于 pigment FBO
            // stroke 是当前笔内容，混入前不扩散
            // ============================================================
//            if (hasNewStamp) {
//                computeVelocityField()
//                logGlState("after computeVelocityField")
//                diffuseFramesRemaining = 3
//            }
//            if (diffuseFramesRemaining > 0) {
//                diffusePigment()
//                diffuseFramesRemaining--
//                logGlState("after diffusePigment")
//
//                // ============================================================
//                // [DEBUG PROBE] 观察点 B：diffuse 后
//                // ============================================================
//                if (hasNewStamp && lastStampCenterX >= 0) {
//                    probeRegion(pigmentFramebuffer, lastStampCenterX, lastStampCenterY,
//                        tag = "pigment afterDiffuse", highPrecision = true)
//                    probeTotalMass(pigmentFramebuffer, "pigment mass afterDiffuse")
//                }
//            }

            // ============================================================
            diffuseFramesRemaining = 0
            // ===========

            //==============测试关闭扩然

            Log.d("GLPaintRenderer", "renderFrame compositing pigment+stroke over canvas")
            stateCache.bindFramebuffer(compositeFramebuffer.handle)
            if (documentWidth > 0 && documentHeight > 0) stateCache.viewport(documentWidth, documentHeight)
            logGlState("after bind compositeFbo")
            compositePigmentOverCanvas(
                requireNotNull(canvasTexture).handle,
                requireNotNull(pigmentTexture).handle,
                requireNotNull(strokeTexture).handle
            )
            logGlState("after compositePigmentOverCanvas")

            // ============================================================
            // [MOD 2026-09-11] endStroke 帧的 composite 已完成，扫描紫色
            // ============================================================
            if (shouldScanPurple) {
                shouldScanPurple = false

                // ============================================================
                // [MOD 2026-09-11] 分层扫描：定位"红蓝交叠"失败在哪一层
                // ============================================================

                // 1. pigment FBO：应含红笔数据（"旧颜色"来源）
                scanColorPixels(pigmentFramebuffer, "SCAN pigment-red") { r, g, b ->
                    r > 100 && g < 80 && b < 80
                }

                // 2. stroke FBO：当前笔应为蓝色（此时还没清 stroke）
                scanColorPixels(strokeFramebuffer, "SCAN stroke-blue") { r, g, b ->
                    b > 100 && r < 80 && g < 80
                }

                // 3. composite FBO：最终显示，应有紫色
                scanColorPixels(compositeFramebuffer, "SCAN composite-purple") { r, g, b ->
                    r > 40 && b > 40 && r > g + 15 && b > g + 15
                }

                // 4. composite FBO：纯蓝区域（说明红蓝没混）
                scanColorPixels(compositeFramebuffer, "SCAN composite-blue") { r, g, b ->
                    b > 200 && r < 80 && g < 80
                }
            }
            // ============================================================

            // ============================================================
            // [DEBUG PROBE] 观察点 C：composite 后
            // ============================================================
            if (hasNewStamp && lastStampCenterX >= 0) {
                probeRegion(pigmentFramebuffer, lastStampCenterX, lastStampCenterY,
                    tag = "pigment afterComposite", highPrecision = true)
                probeRegion(compositeFramebuffer, lastStampCenterX, lastStampCenterY,
                    tag = "composite afterComposite")
            }

            stateCache.bindFramebuffer(0)
            if (context.width > 0 && context.height > 0) stateCache.viewport(context.width, context.height)
            logGlState("after bind default FBO (screen) - before present")

            // 关键：
            GLES20.glClearColor(0f, 0f, 0f, 0f)          // ← alpha = 0
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glEnable(GLES20.GL_BLEND)              // ← 必须有
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)  // ← 必须有
            drawPresent()
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glClearColor(clearRed, clearGreen, clearBlue, clearAlpha)  // 恢复
        }
        renderGraph.ordered().forEach { _ -> Unit }
        val trailingError = GLES20.glGetError()
        if (trailingError != GLES20.GL_NO_ERROR && observedFrameGlError == GLES20.GL_NO_ERROR) {
            observedFrameGlError = trailingError
        }
        lastRenderGlError = observedFrameGlError
        lastFrameTimeMs = (System.nanoTime() - frameStartNanos) / 1_000_000f
    }

    /** Snapshot taken on the GL thread immediately after the captured frame. */
    fun debugRenderMetrics(): Map<String, Any> = mapOf(
        "frameTime" to lastFrameTimeMs,
        "frameTimeMs" to lastFrameTimeMs,
        "glError" to lastRenderGlError
    )

    // ============================================================
    // [MOD PR-2.1] 把 Bitmap 缩放到文档尺寸，覆盖 canvas FBO
    // 使用 glTexImage2D 直接覆盖 canvasTexture
    // ============================================================
    // ============================================================
    // [MOD PR-2.2] 把 Bitmap 作为画布底层内容
    //
    // 语义：
    //   - 图片等比缩放，居中显示，两侧/上下填白
    //   - Y 轴翻转抵消 GLUtils.texImage2D 的默认翻转
    //   - 图片充满整个 canvas FBO，作为"最底层的画布内容"
    //
    // 为什么现在这么做：
    //   - 引入真正的多图层 FBO 合成 = 大重构（不在 PR-2 范围）
    //   - 图片作为"画布内容"是最小侵入方案
    //   - 未来加图层时，图片可以作为 background 图层单独处理，
    //     当前 canvas FBO 的语义天然兼容"最底层"。
    // ============================================================
    private fun blitBackgroundToCanvas(bitmap: Bitmap) {
        val canvasFbo = canvasFramebuffer ?: return
        val canvasTex = canvasTexture ?: return

        val dstW = documentWidth
        val dstH = documentHeight
        val srcW = bitmap.width
        val srcH = bitmap.height
        if (srcW <= 0 || srcH <= 0) return

        // 1. 等比缩放
        val scale = minOf(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)

        // 2. 居中偏移
        val offsetX = (dstW - scaledW) / 2f
        val offsetY = (dstH - scaledH) / 2f

        // 3. 变换矩阵：先缩放 + Y 翻转，再平移到目标位置
        //
        //    因为 GLUtils.texImage2D 会把 Bitmap 的 Y 轴翻转
        //    （Bitmap 顶部 → 纹理底部），所以在这里主动翻回来。
        //
        //    数学推导：
        //      目标 y = -源 y * scale + (offsetY + scaledH)
        //      Bitmap (0, 0)          → (offsetX, offsetY + scaledH)  [画布左下]
        //      Bitmap (0, srcH)       → (offsetX, offsetY)             [画布左上]
        //    注意 Android Canvas 原点在左上角，y 向下增长。
        //
        //    所以 x' = x*scale + offsetX
        //         y' = -y*scale + (offsetY + scaledH)
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, -scale)
            postTranslate(offsetX, offsetY + scaledH)
        }

        // 4. 绘制到最终 Bitmap（白底 + 居中图片）
        val finalBitmap = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val gCanvas = android.graphics.Canvas(finalBitmap)
        gCanvas.drawColor(android.graphics.Color.WHITE)
        gCanvas.drawBitmap(bitmap, matrix, null)

        // 5. 上传到 canvas texture
        stateCache.bindFramebuffer(canvasFbo.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, canvasTex.handle)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, finalBitmap, 0)
        finalBitmap.recycle()

        stateCache.invalidate()
        Log.d("GLPaintRenderer",
            "blitBackgroundToCanvas: src=${srcW}x$srcH scaled=${scaledW}x$scaledH " +
                    "offset=(${offsetX.toInt()},${offsetY.toInt()}) doc=${dstW}x$dstH")
    }
    // ============================================================
    fun setBrushStamps(stamps: List<BrushStamp>) {
        setColoredBrushStamps(stamps.map { it.withCurrentColor() })
    }

    fun addBrushStamps(stamps: List<BrushStamp>) {
        addColoredBrushStamps(stamps.map { it.withCurrentColor() })
    }

    fun setColoredBrushStamps(stamps: List<ColoredBrushStamp>) {
        pendingStamps.set(stamps.toList())
    }

    fun addColoredBrushStamps(stamps: List<ColoredBrushStamp>) {
        if (stamps.isEmpty()) return
        pendingStamps.updateAndGet { current -> current + stamps }
    }

    /** Called by the touch layer for ACTION_UP / ACTION_CANCEL; consumed on the next GL frame. */
    fun requestEndStroke() {
        endStrokeRequested.set(true)
    }

    // ============================================================
    // [MOD PR-2.6] 取消当前笔（多指操作时使用）
    //   与 endStroke 的区别：不 flush 到 pigment，直接丢弃
    // ============================================================
    fun requestCancelStroke() {
        cancelStrokeRequested.set(true)
    }

    private fun cancelStrokeInternal() {
        val strokeFbo = strokeFramebuffer
        if (strokeFbo != null) {
            stateCache.bindFramebuffer(strokeFbo.handle)
            stateCache.viewport(documentWidth, documentHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        strokeActive = false
        pendingStamps.set(emptyList())
        Log.d("GLPaintRenderer", "cancelStroke: stroke FBO cleared (no flush)")
    }
    // ============================================================
    // ============================================================
    // [MOD PR-2.1] 设置画布背景图（在下一帧渲染时应用）
    // 图片会被缩放到文档尺寸铺满 canvas FBO
    // ============================================================
    fun setBackgroundBitmap(bitmap: Bitmap) {
        pendingBackgroundBitmap = bitmap
    }
    // ============================================================

    fun setBrushColor(red: Float, green: Float, blue: Float, alpha: Float = 1f) {
        require(red.isFinite() && green.isFinite() && blue.isFinite() && alpha.isFinite())
        require(red in 0f..1f && green in 0f..1f && blue in 0f..1f && alpha in 0f..1f)

        // ======================= COLOR_TRACE 日志 =======================
        Log.d(
            COLOR_TAG,
            "GLPaintRenderer.setBrushColor 入口: r=$red g=$green b=$blue a=$alpha " +
                    "(旧值: r=$brushRed g=$brushGreen b=$brushBlue a=$brushAlpha)"
        )
        // ================================================================

        brushRed = red
        brushGreen = green
        brushBlue = blue
        brushAlpha = alpha

        // ======================= COLOR_TRACE 日志 =======================
        Log.d(
            COLOR_TAG,
            "GLPaintRenderer.setBrushColor 完成: 新值 r=$brushRed g=$brushGreen b=$brushBlue a=$brushAlpha"
        )
        // ================================================================
    }

    // [MOD 2026-09-11] 设置笔内覆盖度上限，供 pigment shader 使用
    fun setStrokeOpacity(opacity: Float) {
        require(opacity.isFinite() && opacity in 0f..1f)
        strokeOpacityLimit = opacity
    }
    // ============================================================
    // [MOD PR-2.6] 设置当前笔的 flow
    //   每笔开始前由 PaintEngineController 调用
    // ============================================================
    fun setStrokeFlow(flow: Float) {
        currentStrokeFlow = flow.coerceIn(0f, 1f)
    }

    fun setDryPaper(heightAmplitude: Float, grainScale: Float, seed: Int = 0) {
        require(heightAmplitude.isFinite() && heightAmplitude in 0f..1f)
        require(grainScale.isFinite() && grainScale > 0f)
        dryPaperHeightAmplitude = heightAmplitude
        dryPaperGrainScale = grainScale
        dryPaperSeed = seed
    }
    // ============================================================

    // ============================================================
    // [DEBUG LOG 2026-09-10] setBrushTextures 调用追踪
    // 用于排查"切换界面后纹理消失"：确认此方法是否被调用、keys 是什么
    // ============================================================
    fun setBrushTextures(textures: Map<String, Bitmap>) {
        Log.d("GLPaintRenderer", "setBrushTextures CALLED keys=${textures.keys} count=${textures.size}")
        pendingBrushTextures = HashMap(textures)
    }
    // ============================================================

    fun setViewTransform(scale: Float, offsetX: Float, offsetY: Float, rotationRadians: Float) {
        require(scale.isFinite() && scale > 0f)
        require(offsetX.isFinite() && offsetY.isFinite() && rotationRadians.isFinite())
        Log.d("GLPaintRenderer", "setViewTransform scale=$scale off=($offsetX,$offsetY) rot=$rotationRadians")
        viewScale = scale
        viewOffsetX = offsetX
        viewOffsetY = offsetY
        viewRotation = rotationRadians
    }

    fun captureCurrentFrame(): Bitmap? {
        Log.d("GLPaintRenderer", "captureCurrentFrame document=${documentWidth}x$documentHeight")
        context.requireReady()
        // [MOD PR-2 Step 3] 改读 composite FBO，包含"画布底色 + 颜料"。
        // 原因：canvas FBO 只有底色，不含用户笔迹，导出会是空白图。
        // composite FBO 是 renderFrame 每帧的最终输出，才是用户看到的内容。
        val framebuffer = compositeFramebuffer ?: canvasFramebuffer ?: return null
        if (documentWidth <= 0 || documentHeight <= 0) return null

        stateCache.bindFramebuffer(framebuffer.handle)
        val width = documentWidth
        val height = documentHeight
        val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)
        val pixels = IntArray(width * height)
        pixelBuffer.rewind()
        pixelBuffer.get(pixels)

        // ============================================================
        // [MOD PR-2.6] 修复导出红变紫 bug
        //
        // glReadPixels 返回的字节流是 [R,G,B,A]，
        // Android 小端下用 asIntBuffer 读出的 int = 0xAABBGGRR
        // 但 Bitmap.setPixels 期望的 int = 0xAARRGGBB
        // 必须手动把 R 和 B 交换回来。
        // ============================================================
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val b = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val r = p and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val flipped = IntArray(pixels.size)
        for (y in 0 until height) {
            val sourceRow = (height - 1 - y) * width
            System.arraycopy(pixels, sourceRow, flipped, y * width, width)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(flipped, 0, width, 0, 0, width, height)
        }
    }

    fun clearCanvas() {
        pendingStamps.set(emptyList())
        clearRequested.set(true)
    }

    /** Debug/acceptance hook: returns the linear RGBA16F stroke target in top-left row order. */
    fun captureStrokeRgbaForTest(): FloatArray? {
        context.requireReady()
        val framebuffer = strokeFramebuffer ?: return null
        stateCache.bindFramebuffer(framebuffer.handle)
        stateCache.viewport(documentWidth, documentHeight)
        val raw = java.nio.ByteBuffer
            .allocateDirect(documentWidth * documentHeight * 4 * Short.SIZE_BYTES)
            .order(java.nio.ByteOrder.nativeOrder())
            .asShortBuffer()
        GLES30.glReadPixels(
            0,
            0,
            documentWidth,
            documentHeight,
            GLES30.GL_RGBA,
            GLES30.GL_HALF_FLOAT,
            raw,
        )
        if (GLES30.glGetError() != GLES30.GL_NO_ERROR) return null
        val result = FloatArray(documentWidth * documentHeight * 4)
        for (topY in 0 until documentHeight) {
            val glY = documentHeight - 1 - topY
            raw.position(glY * documentWidth * 4)
            val targetOffset = topY * documentWidth * 4
            for (component in 0 until documentWidth * 4) {
                result[targetOffset + component] = halfToFloat(raw.get())
            }
        }
        raw.position(0)
        return result
    }

    private fun halfToFloat(bits: Short): Float {
        val value = bits.toInt() and 0xffff
        val sign = if ((value and 0x8000) == 0) 1f else -1f
        val exponent = (value ushr 10) and 0x1f
        val fraction = value and 0x03ff
        return when (exponent) {
            0 -> sign * Math.scalb(fraction.toFloat(), -24)
            0x1f -> if (fraction == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
            else -> sign * Math.scalb(1f + fraction / 1024f, exponent - 15)
        }
    }

    fun setDocumentSize(width: Int, height: Int) {
        require(width > 0 && height > 0)
        Log.d("GLPaintRenderer", "setDocumentSize $width x $height")
        pendingDocumentSize.set(width to height)
    }

    /**
     * 把本帧 batch 内的多个 stamp 用 GL_MAX 累积到 stroke FBO。
     *
     * 与旧版 drawBrushStampBatch（直接画到 pigment）的区别：
     *   旧版：直接画到 pigment FBO，用 GL_ONE/GL_ONE 累加。
     *         同一笔内多个 stamp 会线性叠加，pigment 迅速饱和，
     *         导致中心三通道饱和 = (34,34,34) ≈ 黑线。
     *   新版：画到 stroke FBO，用 GL_MAX 取每个像素的最大吸收值。
     *         同一笔内不论 stamp 多密，每个像素只贡献一次最大吸收。
     *
     * 调用前提：
     *   1. 已 stateCache.bindFramebuffer(strokeFramebuffer.handle)
     *   2. 已 stateCache.viewport(documentWidth, documentHeight)
     *   3. 笔开始时已清空 stroke（beginStrokeInternal 里做）
     * 调用之后：
     *   笔结束时调用 flushStrokeToPigment() 把 stroke 一次性加到 pigment
     */
    private fun drawBrushStampBatchToStroke(batch: List<ColoredBrushStamp>) {
        if (documentWidth <= 0 || documentHeight <= 0) return
        if (batch.isEmpty()) return
        Log.d("GLPaintRenderer", "drawBrushStampBatchToStroke batch=${batch.size}")

        // ============================================================
        // [MOD PR-2.4] 按条件分流：
        //   小像素 + 程序化圆 → SDF 胶囊（细线抗锯齿）
        //   其他 → 原 stamp 路径
        // ============================================================
        val (sdfStamps, stampStamps) = batch.partition { shouldUseSdf(it.stamp) }

        if (stampStamps.isNotEmpty()) {
            drawStampBatchLarge(stampStamps)
        }
        if (sdfStamps.isNotEmpty()) {
            drawSegmentSdfBatch(sdfStamps)
        }
    }

    private fun shouldUseSdf(stamp: BrushStamp): Boolean {
        // [MOD PR-2.7-rollback] 临时回退 SDF，先恢复笔迹显示
        // SDF 分支有 bug，排查中
        return false
    }
    /**
     * [MOD PR-2.4] 原 stamp 路径（从 drawBrushStampBatchToStroke 抽出来的）
     */
    private fun drawStampBatchLarge(batch: List<ColoredBrushStamp>) {
        val shader = brushStampShader ?: return
        val buffer = brushStampBuffer ?: return
        // ... 原 drawBrushStampBatchToStroke 的完整实现（除去开头的边界检查）
        // 见下方"改动 5.4b"


        // ======================= COLOR_TRACE 日志（只打印第一个，避免刷屏） =======================
        batch.firstOrNull()?.let { first ->
            Log.d(
                COLOR_TAG,
                "drawBrushStampBatchToStroke: 即将提交 uColor, first stamp color = " +
                        "r=${first.red} g=${first.green} b=${first.blue} a=${first.alpha}"
            )
        }
        // ========================================================================================

        // ============================================================
        // [DEBUG PROBE] 记录本次 batch 第一个 stamp 的中心
        // ============================================================
        batch.firstOrNull()?.let { first ->
            lastStampCenterX = first.stamp.center.x.toInt()
            lastStampCenterY = first.stamp.center.y.toInt()
        }
        // ============================================================

        // ============================================================
        // [DEBUG LOG 2026-09-10] 追踪 stamp 的纹理 key 和当前 handle 表
        // 用于排查"切换界面后纹理消失"：
        //   - 如果 key 非 null 但 handle=0 → 纹理未上传
        //   - 如果 key=null → 当前笔刷未指定纹理（程序化圆刷）
        //   - 如果 totalHandles 从 N 变成 0 → 纹理被释放
        // ============================================================
        batch.firstOrNull()?.let { first ->
            val key = first.stamp.textureResourceKey
            val handle = key?.let { brushTextureHandles[it] } ?: 0
            Log.d(
                "GLPaintRenderer",
                "drawBrushStampBatchToStroke first key=$key handle=$handle " +
                        "totalHandles=${brushTextureHandles.size} " +
                        "allKeys=${brushTextureHandles.keys}"
            )
        }
        // ============================================================

        GLES20.glUseProgram(shader.program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.handle)


        // ============================================================
        // [MOD PR-2.6] 混合模式：ADD → MAX
        //
        // 原理：
        //   - stroke FBO 存 coverage（不含 flow）
        //   - MAX 混合保留单个 stamp 的过渡带 → 抗锯齿
        //   - flow 在 pigment_add.frag 里乘 → 浓度正确
        //
        // ADD 混合下，密集 stamp 的过渡带被累加填满 → 硬切 → 锯齿
        // ============================================================
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_MAX)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        // ============================================================

        val position = GLES20.glGetAttribLocation(shader.program, "aUnitPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)

        val center = GLES20.glGetUniformLocation(shader.program, "uCenterDocument")
        val extent = GLES20.glGetUniformLocation(shader.program, "uHalfExtentDocument")
        val size = GLES20.glGetUniformLocation(shader.program, "uDocumentSize")
        val rotation = GLES20.glGetUniformLocation(shader.program, "uRotationRadians")
        GLES20.glUniform2f(size, documentWidth.toFloat(), documentHeight.toFloat())

        val color = GLES20.glGetUniformLocation(shader.program, "uColor")
        val texture = GLES20.glGetUniformLocation(shader.program, "uBrushTexture")
        val useTexture = GLES20.glGetUniformLocation(shader.program, "uUseTexture")
        val textureSamplingMode = GLES20.glGetUniformLocation(shader.program, "uTextureSamplingMode")
        val textureSize = GLES20.glGetUniformLocation(shader.program, "uTextureSize")
        val grainTexture = GLES20.glGetUniformLocation(shader.program, "uGrainTexture")
        val useGrain = GLES20.glGetUniformLocation(shader.program, "uUseGrain")
        val grainTextureSize = GLES20.glGetUniformLocation(shader.program, "uGrainTextureSize")
        val grainScale = GLES20.glGetUniformLocation(shader.program, "uGrainScaleDocumentUnitsPerTexel")
        val grainPhase = GLES20.glGetUniformLocation(shader.program, "uGrainPhaseTexels")
        val grainRotation = GLES20.glGetUniformLocation(shader.program, "uGrainRotationRadians")
        val grainDepth = GLES20.glGetUniformLocation(shader.program, "uGrainDepth")
        val dryLoadLoc = GLES20.glGetUniformLocation(shader.program, "uDryLoad")
        val dryArcLoc = GLES20.glGetUniformLocation(shader.program, "uDryArcLength")
        val bristleDensityLoc = GLES20.glGetUniformLocation(shader.program, "uBristleDensity")
        val paperAffinityLoc = GLES20.glGetUniformLocation(shader.program, "uPaperAffinity")
        val paperAmplitudeLoc = GLES20.glGetUniformLocation(shader.program, "uPaperHeightAmplitude")
        val paperScaleLoc = GLES20.glGetUniformLocation(shader.program, "uPaperGrainScale")
        val drySeedLoc = GLES20.glGetUniformLocation(shader.program, "uDrySeed")
        val dryPressureLoc = GLES20.glGetUniformLocation(shader.program, "uDryPressure")
        val flowLoc = GLES20.glGetUniformLocation(shader.program, "uFlow")
        // [MOD PR-2.7] 纹理 alpha 均值
        val meanLoc = GLES20.glGetUniformLocation(shader.program, "uTextureAlphaMean")





        // ============================================================
        // [MOD 2026-09-10 v3] 传 uAbsorption 给 brush_stamp.frag
        // 与 pigment_composite.frag 保持一致（U_ABSORPTION = 3.0）
        // 若 brush_stamp.frag 未声明 uAbsorption，location == -1，跳过
        // ============================================================
        val absorptionLoc = GLES20.glGetUniformLocation(shader.program, "uAbsorption")
        if (absorptionLoc != -1) {
            GLES20.glUniform1f(absorptionLoc, U_ABSORPTION)
        }
        // ============================================================

        batch.forEach { colored ->
            val stamp = colored.stamp
            val textureHandle = stamp.textureResourceKey?.let { brushTextureHandles[it] } ?: 0
            val grainHandle = stamp.grainResourceKey?.let { brushTextureHandles[it] } ?: 0
            if (textureHandle != 0) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
                GLES20.glUniform1i(texture, 0)
                GLES20.glUniform1f(useTexture, 1f)
                GLES20.glUniform1f(textureSamplingMode, stamp.textureSamplingMode.ordinal.toFloat())
                val dimensions = brushTextureSizes[stamp.textureResourceKey]
                GLES20.glUniform2f(
                    textureSize,
                    dimensions?.first?.toFloat() ?: 1f,
                    dimensions?.second?.toFloat() ?: 1f,
                )
            } else {
                GLES20.glUniform1f(useTexture, 0f)
                GLES20.glUniform1f(textureSamplingMode, 0f)
            }
            if (grainHandle != 0) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, grainHandle)
                GLES20.glUniform1i(grainTexture, 1)
                GLES20.glUniform1f(useGrain, 1f)
                val grainDimensions = brushTextureSizes[stamp.grainResourceKey]
                GLES20.glUniform2f(
                    grainTextureSize,
                    grainDimensions?.first?.toFloat() ?: 1f,
                    grainDimensions?.second?.toFloat() ?: 1f,
                )
                GLES20.glUniform1f(grainScale, stamp.grainScaleDocumentUnitsPerTexel)
                GLES20.glUniform1f(grainPhase, stamp.grainPhaseTexels)
                GLES20.glUniform1f(grainRotation, stamp.grainRotationRadians)
                GLES20.glUniform1f(grainDepth, stamp.grainDepth)
            } else {
                GLES20.glUniform1f(useGrain, 0f)
            }
            GLES20.glUniform2f(center, stamp.center.x, stamp.center.y)
            val effectiveAspect =
                if (stamp.textureSamplingMode == com.dsq.rebackground.paint.brush.BrushTextureSamplingMode.SOURCE_RGBA) {
                    brushTextureSizes[stamp.textureResourceKey]?.let { it.first.toFloat() / it.second.toFloat() }
                        ?: stamp.aspectRatio
                } else {
                    stamp.aspectRatio
                }
            GLES20.glUniform2f(
                extent,
                stamp.diameterDocumentUnits * effectiveAspect / 2f,
                stamp.diameterDocumentUnits / 2f
            )
            GLES20.glUniform1f(rotation, stamp.rotationRadians)
            GLES20.glUniform1f(dryLoadLoc, stamp.dryLoad)
            GLES20.glUniform1f(dryArcLoc, stamp.dryArcLengthDocumentUnits)
            GLES20.glUniform1f(bristleDensityLoc, stamp.bristleDensity)
            GLES20.glUniform1f(paperAffinityLoc, stamp.paperGrainAffinity)
            GLES20.glUniform1f(paperAmplitudeLoc, dryPaperHeightAmplitude)
            GLES20.glUniform1f(paperScaleLoc, dryPaperGrainScale)
            GLES20.glUniform1f(drySeedLoc, (dryPaperSeed + stamp.bristleSeed).toFloat())
            GLES20.glUniform1f(dryPressureLoc, stamp.dryPressure)
            GLES20.glUniform4f(color, colored.red, colored.green, colored.blue, colored.alpha)
            if (flowLoc != -1) {
                GLES20.glUniform1f(flowLoc, colored.flow)
            }

            // ============================================================
            // [MOD PR-2.7] 传纹理 alpha 均值（用于归一化）
            // ============================================================
            if (meanLoc != -1 && textureHandle != 0) {
                val mean = brushTextureAlphaMeans[stamp.textureResourceKey] ?: 1.0f
                GLES20.glUniform1f(meanLoc, mean)
            } else if (meanLoc != -1) {
                // 程序化圆不走纹理，给个默认值
                GLES20.glUniform1f(meanLoc, 1.0f)
            }
            // ============================================================
            // ============================================================
            // [MOD PR-2.5] 传过渡带参数
            // ============================================================
            val aaLoc = GLES20.glGetUniformLocation(shader.program, "uEdgeSoftnessPx")
            val diamLoc = GLES20.glGetUniformLocation(shader.program, "uDiameterPx")
            if (aaLoc != -1) {
                // [MOD PR-2.6] 过渡带 1.0 像素（顶级软件经验值）
                GLES20.glUniform1f(aaLoc, 1.0f)
            }
            if (diamLoc != -1) {
                GLES20.glUniform1f(diamLoc, stamp.diameterDocumentUnits)
            }
            // ============================================================
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        // ============================================================
        // [MOD 2026-09-10 v3] 恢复默认 blend equation
        // 后续 flushStrokeToPigment 使用独立的 blend 状态。
        // ============================================================
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(position)
    }

    // ============================================================
    // [MOD PR-2.4] SDF 胶囊批量渲染
    //   所有胶囊一次构建顶点，一次 draw call
    // ============================================================
    private fun drawSegmentSdfBatch(batch: List<ColoredBrushStamp>) {
        Log.d("GLPaintRenderer",
            "drawSegmentSdfBatch ENTER: batch=${batch.size} " +
                    "shader=${segmentSdfShader != null} buffer=${segmentSdfBuffer != null}")
        val shader = segmentSdfShader ?: return
        val buffer = segmentSdfBuffer ?: return
        if (batch.isEmpty()) return

        val vertexData = buildSdfVertexData(batch)
        Log.d("GLPaintRenderer",
            "drawSegmentSdfBatch vertexData.size=${vertexData.size} " +
                    "first8=${vertexData.take(8).joinToString(",")}")
        if (vertexData.isEmpty()) return
        buffer.update(vertexData)

        GLES20.glUseProgram(shader.program)
        Log.d("GLPaintRenderer",
            "drawSegmentSdfBatch uniforms: " +
                    "pos=${GLES20.glGetAttribLocation(shader.program, "aDocumentPosition")} " +
                    "p0=${GLES20.glGetAttribLocation(shader.program, "aP0")} " +
                    "color=${GLES20.glGetUniformLocation(shader.program, "uColor")} " +
                    "aa=${GLES20.glGetUniformLocation(shader.program, "uAA")} " +
                    "flow=${GLES20.glGetUniformLocation(shader.program, "uFlow")} " +
                    "docSize=${GLES20.glGetUniformLocation(shader.program, "uDocumentSize")}")

        // uniform
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(shader.program, "uDocumentSize"),
            documentWidth.toFloat(), documentHeight.toFloat()
        )
        GLES20.glUniform1f(
            GLES20.glGetUniformLocation(shader.program, "uAA"),
            SDF_AA_PX
        )
        // 同一笔内颜色/flow 一致，取首个
        val first = batch.first()
        GLES20.glUniform4f(
            GLES20.glGetUniformLocation(shader.program, "uColor"),
            first.red, first.green, first.blue, first.alpha
        )
        GLES20.glUniform1f(
            GLES20.glGetUniformLocation(shader.program, "uFlow"),
            first.flow
        )

        // 顶点属性
        val posAttr = GLES20.glGetAttribLocation(shader.program, "aDocumentPosition")
        val p0Attr = GLES20.glGetAttribLocation(shader.program, "aP0")
        val p1Attr = GLES20.glGetAttribLocation(shader.program, "aP1")
        val r0Attr = GLES20.glGetAttribLocation(shader.program, "aR0")
        val r1Attr = GLES20.glGetAttribLocation(shader.program, "aR1")

        val stride = 8 * Float.SIZE_BYTES
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.handle)

        GLES20.glEnableVertexAttribArray(posAttr)
        GLES20.glVertexAttribPointer(posAttr, 2, GLES20.GL_FLOAT, false, stride, 0)

        GLES20.glEnableVertexAttribArray(p0Attr)
        GLES20.glVertexAttribPointer(p0Attr, 2, GLES20.GL_FLOAT, false, stride, 2 * Float.SIZE_BYTES)

        GLES20.glEnableVertexAttribArray(p1Attr)
        GLES20.glVertexAttribPointer(p1Attr, 2, GLES20.GL_FLOAT, false, stride, 4 * Float.SIZE_BYTES)

        GLES20.glEnableVertexAttribArray(r0Attr)
        GLES20.glVertexAttribPointer(r0Attr, 1, GLES20.GL_FLOAT, false, stride, 6 * Float.SIZE_BYTES)

        GLES20.glEnableVertexAttribArray(r1Attr)
        GLES20.glVertexAttribPointer(r1Attr, 1, GLES20.GL_FLOAT, false, stride, 7 * Float.SIZE_BYTES)

        // ============================================================
        // [MOD PR-2.7] SDF 用 MAX 混合
        //   相邻胶囊在共享端点处重叠，MAX 取最大覆盖度
        //   保证端点浓度不加深
        // ============================================================
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_MAX)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)

        val totalVertices = batch.size * 6
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, totalVertices)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(posAttr)
        GLES20.glDisableVertexAttribArray(p0Attr)
        GLES20.glDisableVertexAttribArray(p1Attr)
        GLES20.glDisableVertexAttribArray(r0Attr)
        GLES20.glDisableVertexAttribArray(r1Attr)

        Log.d("GLPaintRenderer",
            "drawSegmentSdfBatch: ${batch.size} capsules, ${totalVertices} vertices")
    }

    // ============================================================
    // [MOD PR-2.4] 构建 SDF 顶点数据
    //   每个 stamp 与下一个 stamp 组成胶囊，最后一个 stamp 与自己组成
    //   退化的圆（胶囊两端相同 = 圆）
    //   顶点布局：每个顶点 8 floats [x, y, p0x, p0y, p1x, p1y, r0, r1]
    // ============================================================
    private fun buildSdfVertexData(batch: List<ColoredBrushStamp>): FloatArray {
        if (batch.isEmpty()) return FloatArray(0)

        val numCapsules = batch.size  // N-1 个线段 + 1 个退化圆
        val data = FloatArray(numCapsules * 6 * 8)

        var idx = 0
        for (i in 0 until numCapsules) {
            val a = batch[i]
            val b = if (i + 1 < batch.size) batch[i + 1] else batch[i]

            val x0 = a.stamp.center.x
            val y0 = a.stamp.center.y
            val x1 = b.stamp.center.x
            val y1 = b.stamp.center.y
            val r0 = a.stamp.diameterDocumentUnits / 2f
            val r1 = b.stamp.diameterDocumentUnits / 2f

            // AABB + padding
            val pad = maxOf(r0, r1) + SDF_AA_PX
            val minX = minOf(x0, x1) - pad
            val maxX = maxOf(x0, x1) + pad
            val minY = minOf(y0, y1) - pad
            val maxY = maxOf(y0, y1) + pad

            // 两个三角形：BL, BR, TR / BL, TR, TL
            val corners = floatArrayOf(
                minX, minY,
                maxX, minY,
                maxX, maxY,
                minX, minY,
                maxX, maxY,
                minX, maxY
            )

            for (c in 0 until 6) {
                val off = idx + c * 8
                data[off]     = corners[c * 2]
                data[off + 1] = corners[c * 2 + 1]
                data[off + 2] = x0
                data[off + 3] = y0
                data[off + 4] = x1
                data[off + 5] = y1
                data[off + 6] = r0
                data[off + 7] = r1
            }
            idx += 6 * 8
        }
        return data
    }

    private fun BrushStamp.withCurrentColor(): ColoredBrushStamp =
        ColoredBrushStamp(this, brushRed, brushGreen, brushBlue, brushAlpha)

    private fun ensureCanvasResources() {
        if (documentWidth <= 0 || documentHeight <= 0) return
        val existing = canvasTexture
        if (existing != null && existing.target.width == documentWidth && existing.target.height == documentHeight) return
        Log.d("GLPaintRenderer", "ensureCanvasResources creating canvas ${documentWidth}x${documentHeight}")
        releaseCanvasResources()
        val texture = GLTexture(context, RenderTarget(documentWidth, documentHeight)).also { it.create() }
        val framebuffer = GLFramebuffer(context, texture).also { it.create() }
        canvasTexture = texture
        canvasFramebuffer = framebuffer
        stateCache.bindFramebuffer(framebuffer.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(clearRed, clearGreen, clearBlue, clearAlpha)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }
    // ============================================================
    // [MOD PR-2.3] 计算 fit 缩放系数
    //   至少一边撑满屏幕：min(screenW/docW, screenH/docH)
    // ============================================================
    private fun computeFitScale(): Float {
        if (documentWidth <= 0 || documentHeight <= 0) return 1f
        val sw = context.width
        val sh = context.height
        if (sw <= 0 || sh <= 0) return 1f
        return minOf(
            sw.toFloat() / documentWidth,
            sh.toFloat() / documentHeight
        )
    }
    // ============================================================

    private fun drawPresent() {
        val shader = presentShader ?: return
        val buffer = presentBuffer ?: return
        val texture = compositeTexture ?: canvasTexture ?: return
        Log.d("GLPaintRenderer", "drawPresent texture=${texture.handle} " +
                "viewScale=$viewScale viewOffset=($viewOffsetX,$viewOffsetY) viewRotation=$viewRotation")
        logGlState("drawPresent before draw")
        GLES20.glUseProgram(shader.program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.handle)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "uTexture"), 0)
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(shader.program, "uViewOffset"),
            viewOffsetX,
            viewOffsetY
        )
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(shader.program, "uViewScale"),
            viewScale,
            viewScale
        )
        GLES20.glUniform1f(
            GLES20.glGetUniformLocation(shader.program, "uViewRotation"),
            viewRotation
        )
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(shader.program, "uScreenSize"),
            context.width.toFloat(),
            context.height.toFloat()
        )
        // [MOD 2026-09-11] 画布宽高比，用于在 NDC 空间保持矩形不变形
        // [MOD 2026-09-11] 画布宽高比，用于在 NDC 空间保持矩形不变形
        val aspect = if (documentHeight > 0)
            documentWidth.toFloat() / documentHeight.toFloat() else 1f
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(shader.program, "uDocumentAspect"),
            aspect, 1f
        )

        // ============================================================
        // [MOD PR-2.3] 画布适配 uniform
        // ============================================================
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(shader.program, "uDocumentSize"),
            documentWidth.toFloat(),
            documentHeight.toFloat()
        )
        val sw = context.width.toFloat()
        val sh = context.height.toFloat()
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(shader.program, "uScreenSize"),
            sw, sh
        )
        GLES20.glUniform1f(
            GLES20.glGetUniformLocation(shader.program, "uFitScale"),
            computeFitScale()
        )
        // ============================================================

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.handle)
        val position = GLES20.glGetAttribLocation(shader.program, "aUnitPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun ensureOffscreenResources() {
        if (documentWidth <= 0 || documentHeight <= 0) return
        val existingPigment = pigmentTexture
        val existingComposite = compositeTexture
        if (existingPigment != null && existingPigment.target.width == documentWidth &&
            existingPigment.target.height == documentHeight &&
            existingComposite != null && existingComposite.target.width == documentWidth &&
            existingComposite.target.height == documentHeight
        ) {
            return
        }

        Log.d("GLPaintRenderer", "ensureOffscreenResources creating pigment/composite/diffuse/stroke " +
                "doc=${documentWidth}x${documentHeight} velocity=${documentWidth / 2}x${documentHeight / 2}")

        releaseOffscreenResources()
        val pigment = GLTexture(context, RenderTarget(documentWidth, documentHeight, RenderTarget.Format.RGBA16F)).also { it.create() }
        val pigmentFbo = GLFramebuffer(context, pigment).also { it.create() }
        val composite = GLTexture(context, RenderTarget(documentWidth, documentHeight)).also { it.create() }
        val compositeFbo = GLFramebuffer(context, composite).also { it.create() }
        val diffuse = GLTexture(context, RenderTarget(documentWidth, documentHeight, RenderTarget.Format.RGBA16F)).also { it.create() }
        val diffuseFbo = GLFramebuffer(context, diffuse).also { it.create() }
        pigmentTexture = pigment
        pigmentFramebuffer = pigmentFbo

        // ============================================================
        // [MOD 2026-09-11] Mixbox ping-pong FBO
        // 与 pigment 同尺寸、同格式（RGBA16F）
        // ============================================================
        val pingPong = GLTexture(context, RenderTarget(documentWidth, documentHeight, RenderTarget.Format.RGBA16F)).also { it.create() }
        val pingPongFbo = GLFramebuffer(context, pingPong).also { it.create() }
        pigmentPingPongTexture = pingPong
        pigmentPingPongFramebuffer = pingPongFbo

        stateCache.bindFramebuffer(pingPongFbo.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        // ============================================================

        compositeTexture = composite
        compositeFramebuffer = compositeFbo
        diffuseTexture = diffuse
        diffuseFramebuffer = diffuseFbo

        // ============================================================
        // [MOD 2026-09-10 v3] stroke FBO（本笔累积）
        // ============================================================
        val stroke = GLTexture(context, RenderTarget(documentWidth, documentHeight, RenderTarget.Format.RGBA16F)).also { it.create() }
        val strokeFbo = GLFramebuffer(context, stroke).also { it.create() }
        strokeTexture = stroke
        strokeFramebuffer = strokeFbo
        // ============================================================

        velocityField?.release()
        velocityField = VelocityField().also {
            it.create((documentWidth / 2).coerceAtLeast(1), (documentHeight / 2).coerceAtLeast(1))
        }

        stateCache.bindFramebuffer(pigmentFbo.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        stateCache.bindFramebuffer(compositeFbo.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(clearRed, clearGreen, clearBlue, clearAlpha)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        stateCache.bindFramebuffer(diffuseFbo.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        stateCache.bindFramebuffer(strokeFbo.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        strokeActive = false
    }

    private fun clearDocumentTargets(
        canvasFramebuffer: GLFramebuffer,
        pigmentFramebuffer: GLFramebuffer,
        compositeFramebuffer: GLFramebuffer
    ) {
        stateCache.bindFramebuffer(canvasFramebuffer.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(clearRed, clearGreen, clearBlue, clearAlpha)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        stateCache.bindFramebuffer(pigmentFramebuffer.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        stateCache.bindFramebuffer(compositeFramebuffer.handle)
        stateCache.viewport(documentWidth, documentHeight)
        GLES20.glClearColor(clearRed, clearGreen, clearBlue, clearAlpha)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        diffuseFramebuffer?.let { diffuseFramebuffer ->
            stateCache.bindFramebuffer(diffuseFramebuffer.handle)
            stateCache.viewport(documentWidth, documentHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        // ============================================================
        // [MOD 2026-09-10 v3] 清空 stroke FBO 并重置笔状态
        // ============================================================
        strokeFramebuffer?.let { strokeFbo ->
            stateCache.bindFramebuffer(strokeFbo.handle)
            stateCache.viewport(documentWidth, documentHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        strokeActive = false
        // ============================================================
    }

    private fun computeVelocityField() {
        val shader = velocityShader ?: return
        val buffer = fullscreenBuffer ?: return
        val velocityField = velocityField ?: return
        val source = pigmentTexture ?: return

        logGlState("computeVelocityField BEFORE bindTarget")
        velocityField.bindTarget()
        logGlState("computeVelocityField AFTER bindTarget")
        GLES20.glUseProgram(shader.program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, source.handle)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "uWetnessTexture"), 0)
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(shader.program, "uTexelSize"),
            1f / documentWidth,
            1f / documentHeight
        )
        GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.program, "uAdvectionStrength"), .15f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.program, "uVelocityScale"), .15f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.handle)
        val position = GLES20.glGetAttribLocation(shader.program, "aUnitPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        logGlState("computeVelocityField AFTER draw")
        velocityField.unbind()
        stateCache.invalidateViewport()
        stateCache.viewport(context.width, context.height)
        logGlState("computeVelocityField AFTER unbind")
    }

    private fun diffusePigment() {
        val shader = pigmentDiffuseShader ?: return
        val buffer = fullscreenBuffer ?: return
        val source = pigmentTexture ?: return
        val velocityField = velocityField ?: return
        val target = diffuseFramebuffer ?: return
        val targetTexture = diffuseTexture ?: return
        Log.d("GLPaintRenderer", "diffusePigment source=${source.handle} target=${target.handle} velocity=${velocityField.texture}")

        stateCache.bindFramebuffer(target.handle)
        stateCache.viewport(documentWidth, documentHeight)
        logGlState("diffusePigment after bind target")

        GLES20.glUseProgram(shader.program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, source.handle)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "uPigmentTexture"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, velocityField.texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "uVelocityTexture"), 1)
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(shader.program, "uTexelSize"),
            1f / documentWidth,
            1f / documentHeight
        )
        GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.program, "uDt"), 1f / 60f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.program, "uDiffusion"), .05f)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.handle)
        val position = GLES20.glGetAttribLocation(shader.program, "aUnitPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        logGlState("diffusePigment after draw")

        val oldTexture = pigmentTexture
        val oldFramebuffer = pigmentFramebuffer
        pigmentTexture = targetTexture
        pigmentFramebuffer = target
        diffuseTexture = oldTexture
        diffuseFramebuffer = oldFramebuffer
        Log.d("GLPaintRenderer", "diffusePigment SWAPPED: pigmentTex now=${pigmentTexture?.handle} pigmentFbo now=${pigmentFramebuffer?.handle}")
    }

    /**
     * [MOD 2026-09-10 v3] composite: canvas × exp(-(pigment + stroke) × uAbsorption)
     *
     * 与 v2 的区别：
     *   v2 只读 pigment，本笔内容通过 addAccumToPigment 每帧累加进 pigment
     *   v3 读 pigment + stroke，本笔内容单独读取，混色语义更清晰
     *
     * 三张纹理绑定：
     *   GL_TEXTURE0: uCanvasTexture  (白底)
     *   GL_TEXTURE1: uPigmentTexture (历史累积吸收系数)
     *   GL_TEXTURE2: uStrokeTexture  (当前笔吸收系数)
     */
    private fun compositePigmentOverCanvas(
        canvasTextureHandle: Int,
        pigmentTextureHandle: Int,
        strokeTextureHandle: Int
    ) {
        val shader = pigmentCompositeShader ?: return
        val buffer = fullscreenBuffer ?: return
        Log.d("GLPaintRenderer", "compositePigmentOverCanvas canvas=$canvasTextureHandle " +
                "pigment=$pigmentTextureHandle stroke=$strokeTextureHandle")
        // [MOD 2026-09-11 诊断] 读 pigment texture 在最近笔迹中心的内容
        if (DEBUG_PROBE && lastStampCenterX >= 0) {
            // 但探针读的是 FBO 绑定，需要知道 pigment 对应的 FBO
            // 用 pigmentFramebuffer 来读
            val pigFbo = pigmentFramebuffer
            if (pigFbo != null) {
                probeRegion(pigFbo, lastStampCenterX, lastStampCenterY,
                    tag = "composite pigIn", highPrecision = true)
            }
        }

        GLES20.glUseProgram(shader.program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, canvasTextureHandle)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "uCanvasTexture"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, pigmentTextureHandle)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "uPigmentTexture"), 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, strokeTextureHandle)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "uStrokeTexture"), 2)
        // [MOD 2026-09-11] 传 uStrokeOpacity
        val opacityLoc = GLES20.glGetUniformLocation(shader.program, "uStrokeOpacity")
        if (opacityLoc != -1) {
            GLES20.glUniform1f(opacityLoc, strokeOpacityLimit)
        }



        // [MOD 2026-09-11] 绑定 Mixbox LUT
        if (mixboxLutHandle != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mixboxLutHandle)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.program, "mixbox_lut"), 3)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffer.handle)
        val position = GLES20.glGetAttribLocation(shader.program, "aUnitPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun releaseCanvasResources() {
        canvasFramebuffer?.release()
        canvasFramebuffer = null
        canvasTexture?.release()
        canvasTexture = null
    }

    private fun releaseOffscreenResources() {
        compositeFramebuffer?.release()
        compositeFramebuffer = null
        compositeTexture?.release()
        compositeTexture = null
        pigmentFramebuffer?.release()
        pigmentFramebuffer = null
        pigmentTexture?.release()
        pigmentTexture = null
        diffuseFramebuffer?.release()
        diffuseFramebuffer = null
        diffuseTexture?.release()
        diffuseTexture = null
        // ============================================================
        // [MOD 2026-09-10 v3] 释放 stroke FBO
        // ============================================================
        strokeFramebuffer?.release()
        strokeFramebuffer = null
        strokeTexture?.release()
        strokeTexture = null
        // ============================================================
        pigmentPingPongFramebuffer?.release()
        pigmentPingPongFramebuffer = null
        pigmentPingPongTexture?.release()
        pigmentPingPongTexture = null
        velocityField?.release()
        velocityField = null
    }

    // ============================================================
    // [DEBUG LOG 2026-09-10] uploadBrushTexturesIfNeeded 调用追踪
    // 用于排查"切换界面后纹理消失"：
    //   - 是否被调用（pendingBrushTextures 非空时）
    //   - 上传哪些 key
    //   - 清理哪些 key
    // ============================================================
    private fun uploadBrushTexturesIfNeeded() {
        val textures = pendingBrushTextures ?: return
        Log.d("GLPaintRenderer", "uploadBrushTexturesIfNeeded keys=${textures.keys} count=${textures.size} " +
                "oldHandles=${brushTextureHandles.size}")
        pendingBrushTextures = null

        val requestedKeys = textures.keys.toSet()
        brushTextureHandles.keys.filter { it !in requestedKeys }.forEach { key ->
            brushTextureHandles.remove(key)?.let { handle ->
                Log.d("GLPaintRenderer", "uploadBrushTexturesIfNeeded DELETE key=$key handle=$handle")
                GLES20.glDeleteTextures(1, intArrayOf(handle), 0)
            }
            brushTextureSizes.remove(key)
        }

        textures.forEach { (key, bitmap) ->
            brushTextureHandles[key]?.let { old ->
                Log.d("GLPaintRenderer", "uploadBrushTexturesIfNeeded REPLACE key=$key oldHandle=$old")
                GLES20.glDeleteTextures(1, intArrayOf(old), 0)
            }
            brushTextureHandles[key] = createBrushTexture(bitmap)
            brushTextureSizes[key] = bitmap.width to bitmap.height
            // ============================================================
            // [MOD PR-2.7] 同时计算 alpha 均值
            // ============================================================
            brushTextureAlphaMeans[key] = computeAlphaMean(bitmap)
            // ============================================================
        }
        Log.d("GLPaintRenderer", "uploadBrushTexturesIfNeeded DONE newHandles=${brushTextureHandles.size} " +
                "keys=${brushTextureHandles.keys}")
    }
    // ============================================================

    // ============================================================
    // [DEBUG LOG 2026-09-10] createBrushTexture 调用追踪
    // 用于排查"切换界面后纹理消失"：每次上传的 handle 是多少
    // ============================================================
    private fun createBrushTexture(bitmap: Bitmap): Int {
        val handles = IntArray(1)
        GLES20.glGenTextures(1, handles, 0)
        check(handles[0] != 0) { "Unable to allocate brush texture" }
        Log.d("GLPaintRenderer", "createBrushTexture handle=${handles[0]} " +
                "size=${bitmap.width}x${bitmap.height}")
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handles[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return handles[0]
    }
    // ============================================================

    override fun release() {
        brushTextureHandles.values.forEach { handle ->
            GLES20.glDeleteTextures(1, intArrayOf(handle), 0)
        }
        brushTextureHandles.clear()
        brushTextureSizes.clear()
        // [MOD PR-2.7] 清理 alpha 均值缓存
        brushTextureAlphaMeans.clear()
        if (mixboxLutHandle != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(mixboxLutHandle), 0)
            mixboxLutHandle = 0
        }
        brushStampBuffer?.release()

        // [MOD PR-2.4] SDF 资源
        segmentSdfBuffer?.release()
        segmentSdfBuffer = null
        segmentSdfShader?.release()
        segmentSdfShader = null


        brushStampBuffer = null
        brushStampShader?.release()
        brushStampShader = null
        presentBuffer?.release()
        presentBuffer = null
        presentShader?.release()
        presentShader = null
        fullscreenBuffer?.release()
        fullscreenBuffer = null
        pigmentDiffuseShader?.release()
        pigmentDiffuseShader = null
        pigmentCompositeShader?.release()
        pigmentCompositeShader = null
        // ============================================================
        // [MOD 2026-09-10 v3] 释放 pigment_add shader
        // ============================================================
        pigmentAddShader?.release()
        pigmentAddShader = null
        // ============================================================
        velocityShader?.release()
        velocityShader = null
        releaseCanvasResources()
        releaseOffscreenResources()
        stateCache.invalidate()
        context.markReleased()
    }
}
