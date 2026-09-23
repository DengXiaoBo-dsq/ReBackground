package com.dsq.rebackground.paint.rendering.gl

import android.content.Context
import android.graphics.Bitmap
import com.dsq.rebackground.paint.paper.PaperDefinition
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.dsq.rebackground.paint.brush.BrushStamp
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
/**
 * Optional Android host for the new renderer. It is intentionally separate from legacy  and starts hidden in
 * the existing paint screen until a later migration explicitly enables the GPU canvas.
 */
class PaintGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    private var cachedDocumentWidth = -1
    private var cachedDocumentHeight = -1
    private var debugGlReadyListener: (() -> Unit)? = null
    private val paintRenderer = GLPaintRenderer(
        brushStampVertexSource = readAsset("paint/shaders/brush_stamp.vert"),
        brushStampFragmentSource = readAsset("paint/shaders/brush_stamp.frag"),
        presentVertexSource = readAsset("paint/shaders/present.vert"),
        presentFragmentSource = readAsset("paint/shaders/present.frag"),
        fullscreenVertexSource = readAsset("paint/shaders/fullscreen.vert"),
        velocityFragmentSource = readAsset("paint/shaders/velocity.frag"),
        pigmentDiffuseFragmentSource = readAsset("paint/shaders/pigment_diffuse.frag"),
        pigmentCompositeFragmentSource = readShaderWithMixbox("paint/shaders/pigment_composite.frag"),
        pigmentAddFragmentSource = readShaderWithMixbox("paint/shaders/pigment_add.frag"),
        mixboxLutBitmap = readAssetBitmap("paint/shaders/mixbox_lut.png"),
        paperCompositeFragmentSource = readAsset("paint/shaders/paper_composite.frag"),
        particleVertexSource = readAsset("paint/shaders/particle.vert"),
        particleFragmentSource = readAsset("paint/shaders/particle.frag"),
        // [MOD PR-2.4] SDF 胶囊着色器
        segmentSdfVertexSource = readAsset("paint/shaders/segment_sdf.vert"),
        segmentSdfFragmentSource = readAsset("paint/shaders/segment_sdf.frag")
    )
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)             // ← 4 个 8
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)   // ← 必须有
        preserveEGLContextOnPause = true
        // [G0] 匿名 Renderer 转调 paintRenderer；仅在 onSurfaceCreated 末尾触发
        // debugGlReadyListener（生产侧保持 null，行为零变化）。
        setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                paintRenderer.onSurfaceCreated()
            }
            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                paintRenderer.onSurfaceChanged(width, height)
                debugGlReadyListener?.invoke()
            }
            override fun onDrawFrame(gl: GL10?) {
                paintRenderer.renderFrame()
            }
        })
        renderMode = RENDERMODE_WHEN_DIRTY
    }


    fun setDebugGlReadyListener(listener: (() -> Unit)?) {
        debugGlReadyListener = listener
    }

    // ============================================================
    /** Requests one GPU frame. This has no effect on legacy drawing content. */
    fun renderOnce() = requestRender()

    fun setDocumentSize(documentWidth: Int, documentHeight: Int) {
        if (markDocumentSizeIfChanged(documentWidth, documentHeight)) {
            queueEvent { paintRenderer.setDocumentSize(documentWidth, documentHeight) }
        }
        requestRender()
    }

    fun showBrushStamps(stamps: List<BrushStamp>, documentWidth: Int, documentHeight: Int) {
        val sizeChanged = markDocumentSizeIfChanged(documentWidth, documentHeight)
        queueEvent {
            if (sizeChanged) paintRenderer.setDocumentSize(documentWidth, documentHeight)
            paintRenderer.setBrushStamps(stamps)
        }
        requestRender()
    }

    /** Enqueue a single stamp so it is painted exactly once into the persistent document framebuffer. */
    fun addBrushStamp(stamp: BrushStamp, documentWidth: Int, documentHeight: Int) {
        val sizeChanged = markDocumentSizeIfChanged(documentWidth, documentHeight)
        queueEvent {
            if (sizeChanged) paintRenderer.setDocumentSize(documentWidth, documentHeight)
            paintRenderer.addBrushStamps(listOf(stamp))
        }
        requestRender()
    }

    fun showColoredBrushStamps(stamps: List<ColoredBrushStamp>, documentWidth: Int, documentHeight: Int) {
        val sizeChanged = markDocumentSizeIfChanged(documentWidth, documentHeight)
        queueEvent {
            if (sizeChanged) paintRenderer.setDocumentSize(documentWidth, documentHeight)
            paintRenderer.setColoredBrushStamps(stamps)
        }
        requestRender()
    }

    fun addColoredBrushStamp(stamp: ColoredBrushStamp, documentWidth: Int, documentHeight: Int) {
        val sizeChanged = markDocumentSizeIfChanged(documentWidth, documentHeight)
        queueEvent {
            if (sizeChanged) paintRenderer.setDocumentSize(documentWidth, documentHeight)
            paintRenderer.addColoredBrushStamps(listOf(stamp))
        }
        requestRender()
    }

    /** Signals that the current ACTION_UP / ACTION_CANCEL ended the active GPU stroke. */
    fun endStroke() {
        paintRenderer.requestEndStroke()
        requestRender()
    }

    // ============================================================
    // [MOD PR-2.6] 取消当前笔（多指操作时用）
    // ============================================================
    fun cancelStroke() {
        paintRenderer.requestCancelStroke()
        requestRender()
    }
    // ============================================================
    fun setBrushColor(red: Float, green: Float, blue: Float, alpha: Float = 1f) {
        paintRenderer.setBrushColor(red, green, blue, alpha)
        requestRender()
    }

    // [MOD 2026-09-11] 转发笔内覆盖度上限给 shader
    fun setStrokeOpacity(opacity: Float) {
        paintRenderer.setStrokeOpacity(opacity)
    }

    // ============================================================
    // [MOD PR-2.6] 转发 flow 给 Renderer
    // ============================================================
    fun setStrokeFlow(flow: Float) {
        paintRenderer.setStrokeFlow(flow)
    }

    fun setDryPaper(heightAmplitude: Float, grainScale: Float, seed: Int = 0) {
        paintRenderer.setDryPaper(heightAmplitude, grainScale, seed)
        requestRender()
    }

    fun setPaper(paper: PaperDefinition) {
        paintRenderer.setPaper(paper)
        requestRender()
    }
    // ============================================================

    fun setBrushTextures(textures: Map<String, Bitmap>) {
        paintRenderer.setBrushTextures(textures)
        requestRender()
    }
    // ============================================================
    // [MOD PR-2.1] 设置画布背景图（用于"用图片创建画布"）
    // ============================================================
    fun setBackgroundBitmap(bitmap: Bitmap) {
        paintRenderer.setBackgroundBitmap(bitmap)
        requestRender()
    }
    // ============================================================

    fun setViewTransform(scale: Float, offsetX: Float, offsetY: Float, rotationRadians: Float) {
        paintRenderer.setViewTransform(scale, offsetX, offsetY, rotationRadians)
        requestRender()
    }
    // ============================================================
    // [MOD PR-2.3] 导出图片（等 GL surface 就绪后再执行）
    //
    // 关键问题：从 Settings 返回时，onActivityResult 立即触发导出，
    // 此时 GL surface 已销毁、context 未重建完成。
    // 只检查 View 尺寸（始终有效）不够 —— 必须等 isSurfaceReady。
    //
    // 最大等待 2 秒（40 次 × 50ms），超时返回 null。
    // ============================================================
    fun captureBitmap(callback: (Bitmap?) -> Unit) {
        waitForSurfaceAndCapture(callback, retries = 40)
    }

    fun captureBitmapWithMetrics(callback: (Bitmap?, Map<String, Any>) -> Unit) {
        waitForSurfaceAndCaptureWithMetrics(callback, retries = 40)
    }

    fun captureStrokeRgbaForTest(callback: (FloatArray?, Map<String, Any>) -> Unit) {
        if (!paintRenderer.isSurfaceReady) {
            postDelayed({ captureStrokeRgbaForTest(callback) }, 50)
            return
        }
        queueEvent {
            try {
                paintRenderer.renderFrame()
                val pixels = paintRenderer.captureStrokeRgbaForTest()
                val metrics = paintRenderer.debugRenderMetrics()
                post { callback(pixels, metrics) }
            } catch (e: Exception) {
                android.util.Log.e("PaintGLSurfaceView", "captureStrokeRgbaForTest failed", e)
                post { callback(null, mapOf("glError" to -1)) }
            }
        }
    }

    private fun waitForSurfaceAndCaptureWithMetrics(
        callback: (Bitmap?, Map<String, Any>) -> Unit,
        retries: Int
    ) {
        if (paintRenderer.isSurfaceReady) {
            queueEvent {
                try {
                    paintRenderer.renderFrame()
                    val bitmap = paintRenderer.captureCurrentFrame()
                    val metrics = paintRenderer.debugRenderMetrics()
                    post { callback(bitmap, metrics) }
                } catch (e: Exception) {
                    android.util.Log.e("PaintGLSurfaceView", "captureBitmapWithMetrics failed", e)
                    post { callback(null, mapOf("frameTime" to 0f, "frameTimeMs" to 0f, "glError" to -1)) }
                }
            }
            return
        }
        if (retries <= 0) {
            callback(null, mapOf("frameTime" to 0f, "frameTimeMs" to 0f, "glError" to -1))
            return
        }
        postDelayed({ waitForSurfaceAndCaptureWithMetrics(callback, retries - 1) }, 50)
    }

    private fun waitForSurfaceAndCapture(callback: (Bitmap?) -> Unit, retries: Int) {
        if (paintRenderer.isSurfaceReady) {
            doCapture(callback)
            return
        }
        if (retries <= 0) {
            android.util.Log.e("PaintGLSurfaceView",
                "captureBitmap: surface never became ready (timeout)")
            callback(null)
            return
        }
        postDelayed({ waitForSurfaceAndCapture(callback, retries - 1) }, 50)
    }

    private fun doCapture(callback: (Bitmap?) -> Unit) {
        queueEvent {
            try {
                // 主动渲染一帧，保证：
                //   1. endStrokeRequested 被消费
                //   2. pendingStamps 被绘制
                //   3. composite FBO 更新到最新
                paintRenderer.renderFrame()
                val bitmap = paintRenderer.captureCurrentFrame()
                post { callback(bitmap) }
            } catch (e: Exception) {
                android.util.Log.e("PaintGLSurfaceView", "captureBitmap failed", e)
                post { callback(null) }
            }
        }
    }


    override fun onPause() {
        // [MOD PR-2.3] 通知 Renderer 标记 surface 丢失
        paintRenderer.markSurfaceLost()
        super.onPause()
    }
    fun clearCanvas() {
        paintRenderer.clearCanvas()
        requestRender()
    }

    private fun readAsset(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    private fun readAssetBitmap(path: String): android.graphics.Bitmap =
        context.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }

    private fun readShaderWithMixbox(shaderPath: String): String {
        // [MOD 2026-09-11] 清洗 BOM 和换行符，避免 GLSL 编译器读到非法字符
        fun clean(s: String): String = s
            .replace("\uFEFF", "")   // UTF-8 BOM
            .replace("\r\n", "\n")   // Windows 换行
            .replace("\r", "\n")     // 旧 Mac 换行

        val mixbox = clean(readAsset("paint/shaders/mixbox.glsl"))
        val shader = clean(readAsset(shaderPath))
        return "precision highp float;\n" + mixbox + "\n" + shader
    }

    private fun markDocumentSizeIfChanged(width: Int, height: Int): Boolean {
        if (width != cachedDocumentWidth || height != cachedDocumentHeight) {
            cachedDocumentWidth = width
            cachedDocumentHeight = height
            return true
        }
        return false
    }
}
