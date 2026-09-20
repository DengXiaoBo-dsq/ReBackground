package com.dsq.rebackground.paint.ui

import android.view.MotionEvent
import android.util.Log
import com.dsq.rebackground.paint.brush.BrushDefinition
import com.dsq.rebackground.paint.brush.BrushGenerator
import com.dsq.rebackground.paint.brush.BrushRuntimeState
import com.dsq.rebackground.paint.brush.BrushStamp
import com.dsq.rebackground.paint.core.PaintCommand
import com.dsq.rebackground.paint.core.PaintDocument
import com.dsq.rebackground.paint.core.PaintLayer
import com.dsq.rebackground.paint.history.HistoryManager
import com.dsq.rebackground.paint.input.DocumentSpaceTransform
import com.dsq.rebackground.paint.input.PointerInputProcessor
import com.dsq.rebackground.paint.math.Vec2
import com.dsq.rebackground.paint.pigment.PigmentColor
import com.dsq.rebackground.paint.rendering.gl.ColoredBrushStamp
import com.dsq.rebackground.paint.rendering.gl.PaintGLSurfaceView
import com.dsq.rebackground.paint.stroke.StrokePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.sin
import com.dsq.rebackground.paint.debug.PaintDebugMetrics

class PaintEngineController(private val surface: PaintGLSurfaceView) {
    // [MOD PR-2.3] 屏幕（GLSurfaceView）尺寸
    private var screenWidth = 1
    private var screenHeight = 1
    // document（画布）尺寸
    private var documentWidth = 1
    private var documentHeight = 1
    private var document = PaintDocument(1, 1).also { it.addLayer(PaintLayer("ink")) }
    private var history = HistoryManager(document)
    private var brush = BrushDefinition("default-round", "默认圆形", 24f)
    private var color = PigmentColor(0f, 0f, 0f)
    private var activeLayerId = "ink"
    private var viewScale = 1f
    private var viewOffsetX = 0f
    private var viewOffsetY = 0f
    private var viewRotation = 0f
    private var transforming = false
    private var transformPointerId0 = -1
    private var transformPointerId1 = -1
    private var lastMidX = 0f
    private var lastMidY = 0f
    private var lastDistance = 0f
    private var lastAngle = 0f
    private val generator = BrushGenerator()
    private val generatorStates = HashMap<Int, BrushRuntimeState>()

    private val metrics = PaintDebugMetrics()
    private var strokeIdCounter = 0L

    // 调试开关
    private val TRACE_XFORM = true

    // [MOD 2026-09-11] 当前笔的不透明度上限，传给 shader 的 uStrokeOpacity
    @Volatile private var currentStrokeOpacity = 1f

    // [MOD 2026-09-10] L1 防线：ACTION_UP 之后残留点拦截
    @Volatile private var strokeJustEnded = false
    // [MOD 2026-09-11] 多指锁定：一旦触摸序列中出现过双指，
// 本次 ACTION_DOWN~ACTION_UP 期间禁止绘制
    @Volatile private var multiTouchLocked = false

    private val inputProcessor = PointerInputProcessor(
        transform = DocumentSpaceTransform { x, y -> viewToDocument(x, y) },
        onStrokeFinished = { stroke ->
            history.apply(PaintCommand.AddStroke(activeLayerId, stroke, brush, color))
            generatorStates.clear()
            strokeJustEnded = true
            surface.endStroke()
            // [G0]
            metrics.endStrokeAndLog(generator.strokeStampCount)
        },
        onStrokeCancelled = {
            strokeJustEnded = true
            surface.endStroke()
            // [G0]
            metrics.endStrokeAndLog(generator.strokeStampCount)
        },
        onStrokePoint = ::onStrokePoint
    )

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    // ============================================================
    // [MOD PR-2.3] attach 现在接收 4 个尺寸
    //   screenW/H：GLSurfaceView 屏幕尺寸（决定视口）
    //   docW/H：画布尺寸（决定 FBO 尺寸与坐标系）
    // ============================================================
    fun attach(screenW: Int, screenH: Int, docW: Int, docH: Int) {
        require(screenW > 0 && screenH > 0 && docW > 0 && docH > 0)
        screenWidth = screenW
        screenHeight = screenH
        documentWidth = docW
        documentHeight = docH
        document = PaintDocument(docW, docH).also { it.addLayer(PaintLayer("ink")) }
        history = HistoryManager(document)
        activeLayerId = "ink"
        generatorStates.clear()
        surface.setDocumentSize(docW, docH)
        surface.clearCanvas()
    }

    // ============================================================
    // [MOD PR-2.3] 单独设置 document 尺寸（图片创建画布时使用）
    //   用于在 attach 之后更新画布尺寸（重建 FBO）
    // ============================================================
    fun setDocumentSize(newDocW: Int, newDocH: Int) {
        if (newDocW <= 0 || newDocH <= 0) return
        if (newDocW == documentWidth && newDocH == documentHeight) return
        documentWidth = newDocW
        documentHeight = newDocH
        document = PaintDocument(newDocW, newDocH).also { it.addLayer(PaintLayer("ink")) }
        history = HistoryManager(document)
        activeLayerId = "ink"
        generatorStates.clear()
        surface.setDocumentSize(newDocW, newDocH)
        surface.clearCanvas()
    }
    fun onMotionEvent(event: MotionEvent): Boolean {
        Log.d("PaintEngineController",
            "onMotionEvent action=${event.actionMasked} pointers=${event.pointerCount} " +
                    "multiTouchLocked=$multiTouchLocked")

        // ============================================================
        // [MOD 2026-09-11 v4] 优先处理 ACTION_DOWN 重置
        //
        // 修复：上一版里 multiTouchLocked 拦截在 ACTION_DOWN 之前，
        //       导致锁定状态永远无法重置，用户再也画不出线条。
        //
        // 正确顺序：ACTION_DOWN 时必须先重置所有状态，再判断路径。
        // ============================================================

        // 1. ACTION_DOWN：新触摸序列，无条件重置
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            strokeJustEnded = false
            multiTouchLocked = false
            generatorStates.clear()
            // [G0]
            metrics.beginStroke(++strokeIdCounter)
            generator.beginStroke()
        }
        // 2. ACTION_CANCEL：结束整个触摸序列
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            transforming = false
            transformPointerId0 = -1
            transformPointerId1 = -1
            strokeJustEnded = true
            multiTouchLocked = false
            surface.cancelStroke()
            // [G0]
            metrics.endStrokeAndLog(generator.strokeStampCount)
            return true
        }

        // 3. ACTION_UP：最后一个手指抬起，结束序列，解锁
        // 3. ACTION_UP：最后一个手指抬起，结束序列，解锁
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            strokeJustEnded = true
            multiTouchLocked = false
            // [MOD PR-2.6] 缩放结束后必须清 transforming，
            // 否则下一笔 ACTION_DOWN 会走 "transform 恢复检测" 分支被吞掉
            transforming = false
            transformPointerId0 = -1
            transformPointerId1 = -1
            return inputProcessor.onMotionEvent(event)
        }

        if (event.pointerCount >= 2) {
            if (!multiTouchLocked) {


                multiTouchLocked = true
                strokeJustEnded = true
                surface.cancelStroke()
                metrics.markMultiTouchCancelled()   // [G0]
                Log.d("PaintEngineController", "multiTouchLocked = true, stroke cancelled")
            }
            if (!transforming) beginTransform(event)
            updateTransform(event)
            return true
        }
        // 5. 单指 + 已锁定：1指→2指→1指 的过渡期，忽略
        if (multiTouchLocked) {
            Log.d("PaintEngineController",
                "onMotionEvent single-pointer ignored (multiTouchLocked)")
            return true
        }

        // 6. transform 恢复检测
        if (transforming) {
            transforming = false
            transformPointerId0 = -1
            transformPointerId1 = -1
            return true
        }

        // 7. 正常单指绘制
        return inputProcessor.onMotionEvent(event)
    }

    fun setBrush(definition: BrushDefinition) {
        brush = definition
        generatorStates.clear()
    }

    fun setColor(red: Float, green: Float, blue: Float) {
        color = PigmentColor(red, green, blue)
        surface.setBrushColor(red, green, blue)
    }

    fun resetViewTransform() {
        viewScale = 1f
        viewOffsetX = 0f
        viewOffsetY = 0f
        viewRotation = 0f
        surface.setViewTransform(viewScale, viewOffsetX, viewOffsetY, viewRotation)
    }

    fun layers(): List<PaintLayer> = document.layers

    fun activeLayer(): PaintLayer = requireNotNull(document.layer(activeLayerId))

    fun addLayer(name: String): PaintLayer {
        val id = "layer_${System.currentTimeMillis()}"
        val layer = PaintLayer(id, name, true)
        document.addLayer(layer)
        activeLayerId = id
        return layer
    }

    fun selectLayer(id: String) {
        requireNotNull(document.layer(id)) { "Unknown layer: $id" }
        activeLayerId = id
    }

    fun toggleLayerVisibility(id: String) {
        requireNotNull(document.layer(id)) { "Unknown layer: $id" }.visible =
            !requireNotNull(document.layer(id)).visible
        replayDocument()
    }

    fun removeLayer(id: String) {
        require(document.layers.size > 1) { "At least one layer is required" }
        requireNotNull(document.layer(id)) { "Unknown layer: $id" }
        document.layers.toList()
            .filter { it.id == id }
            .forEach { document.removeLayer(it) }
        if (activeLayerId == id) activeLayerId = document.layers.first().id
        history = HistoryManager(document)
        replayDocument()
    }

    fun undo(): Boolean {
        if (!history.undo()) return false
        replayDocument()
        return true
    }

    fun redo(): Boolean {
        if (!history.redo()) return false
        replayDocument()
        return true
    }

    fun clear() {
        document = PaintDocument(documentWidth, documentHeight).also { it.addLayer(PaintLayer("ink")) }
        history = HistoryManager(document)
        activeLayerId = "ink"
        generatorStates.clear()
        surface.clearCanvas()
    }

    private fun onStrokePoint(pointerId: Int, point: StrokePoint) {
        if (strokeJustEnded) {
            Log.d("PaintEngineController",
                "onStrokePoint intercepted after stroke end: pointer=$pointerId")
            return
        }
        if (documentWidth <= 1 || documentHeight <= 1) return

        val p = point.position
        val docW = documentWidth.toFloat()
        val docH = documentHeight.toFloat()
        val isOob = p.x < 0f || p.x > docW || p.y < 0f || p.y > docH

        // [MOD 2026-09-11 DEBUG v2] 完整上下文，用于定位长直线
        val lastPosDebug = generatorStates[pointerId]?.lastPosition
        Log.d("STROKE",
            "ptr=$pointerId doc=(${p.x},${p.y}) oob=$isOob " +
                    "last=(${lastPosDebug?.x},${lastPosDebug?.y}) " +
                    "scale=$viewScale rot=$viewRotation " +
                    "off=(${viewOffsetX},${viewOffsetY}) " +
                    "locked=$multiTouchLocked transforming=$transforming " +
                    "activePtrs=${generatorStates.keys}")

        if (isOob) {
            Log.d("PaintEngineController",
                "onStrokePoint out of bounds: pointer=$pointerId x=${p.x} y=${p.y}")
            metrics.markOobPoint()              // [G0]
            generatorStates.remove(pointerId)
            return
        }

        val previous = generatorStates[pointerId] ?: BrushRuntimeState()
        val output = generator.generate(brush, point, previous)
        generatorStates[pointerId] = output.nextState

        currentStrokeOpacity = brush.opacity
        surface.setStrokeOpacity(brush.opacity)
        // ============================================================
        // [MOD PR-2.6] 传 flow（缺失 = flow 停在上一笔的值）
        // ============================================================
        surface.setStrokeFlow(brush.flow)


        val currentStamp = output.stamp
        val lastPos = previous.lastPosition

        // [MOD 2026-09-11 DEBUG v2] 记录 stamp 直径，检测直径突变
        Log.d("STAMP",
            "ptr=$pointerId diameter=${currentStamp.diameterDocumentUnits} " +
                    "center=(${currentStamp.center.x},${currentStamp.center.y}) " +
                    "aspect=${currentStamp.aspectRatio} rot=${currentStamp.rotationRadians}")

        var interpCount = 0                     // [G0]
        var largeJump = false                   // [G0]

        if (lastPos != null) {
            val dx = currentStamp.center.x - lastPos.x
            val dy = currentStamp.center.y - lastPos.y
            val distance = hypot(dx, dy)

            val spacingThreshold =
                (currentStamp.diameterDocumentUnits * 0.15f).coerceAtLeast(0.05f)

            val MAX_STROKE_JUMP = 200f
            if (distance > MAX_STROKE_JUMP) {
                largeJump = true                // [G0]
                Log.w("PaintEngineController",
                    "cross-stroke jump detected: ...")
                generatorStates[pointerId] = BrushRuntimeState()
            } else if (distance > spacingThreshold) {
                val steps = (distance / spacingThreshold).toInt().coerceIn(1, 256)

                if (steps > 50) {
                    Log.w("SUSPECT",
                        "LARGE_INTERP steps=$steps ...")
                }

                for (i in 1 until steps) {
                    val t = i.toFloat() / steps.toFloat()
                    val interpX = lastPos.x + dx * t
                    val interpY = lastPos.y + dy * t
                    val interpStamp = BrushStamp(
                        center = Vec2(interpX, interpY),
                        diameterDocumentUnits = currentStamp.diameterDocumentUnits,
                        aspectRatio = currentStamp.aspectRatio,
                        rotationRadians = currentStamp.rotationRadians,
                        textureResourceKey = currentStamp.textureResourceKey
                    )
                    surface.addColoredBrushStamp(
                        ColoredBrushStamp(
                            interpStamp,
                            color.red, color.green, color.blue,
                            alpha = 1f,
                            flow = brush.flow
                        ),
                        documentWidth,
                        documentHeight
                    )
                    interpCount++               // [G0]
                }
            }
        }

        surface.addColoredBrushStamp(
            ColoredBrushStamp(
                currentStamp,
                color.red, color.green, color.blue,
                alpha = 1f,
                flow = brush.flow
            ),
            documentWidth,
            documentHeight
        )
        metrics.onPoint(point, currentStamp, interpCount, largeJump)
    }
    private fun replayDocument() {
        surface.clearCanvas()
        // [G0] replay 不参与 metrics，但需重置 generator stamp 计数
        generator.beginStroke()

        val stamps = mutableListOf<ColoredBrushStamp>()
        document.layers.filter { it.visible }.flatMap { it.commands }
            .filterIsInstance<PaintCommand.AddStroke>()
            .forEach { command ->
                var state = BrushRuntimeState()
                val commandBrush = command.brush ?: brush
                val commandColor = command.color ?: color
                command.stroke.points.forEach { point ->
                    val output = generator.generate(commandBrush, point, state)
                    state = output.nextState
                    stamps += ColoredBrushStamp(
                        output.stamp,
                        commandColor.red,
                        commandColor.green,
                        commandColor.blue,
                        alpha = 1f,
                        flow = commandBrush.flow
                    )
                }
            }
        surface.showColoredBrushStamps(stamps, documentWidth, documentHeight)
    }

    private fun beginTransform(event: MotionEvent) {
        transforming = true
        transformPointerId0 = event.getPointerId(0)
        transformPointerId1 = event.getPointerId(1)
        updateTransformMetrics(event)
    }

    private fun updateTransform(event: MotionEvent) {
        if (event.pointerCount < 2) return
        val index0 = event.findPointerIndex(transformPointerId0)
        val index1 = event.findPointerIndex(transformPointerId1)
        if (index0 < 0 || index1 < 0) {
            transformPointerId0 = event.getPointerId(0)
            transformPointerId1 = event.getPointerId(1)
            updateTransformMetrics(event)
            return
        }

        val x0 = event.getX(index0)
        val y0 = event.getY(index0)
        val x1 = event.getX(index1)
        val y1 = event.getY(index1)
        val midX = (x0 + x1) * .5f
        val midY = (y0 + y1) * .5f
        val distance = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
        val angle = atan2((y1 - y0).toDouble(), (x1 - x0).toDouble()).toFloat()

        if (lastDistance > 0f && distance > 0f) {
            viewScale = (viewScale * distance / lastDistance).coerceIn(.2f, 5f)
        }
        if (documentWidth > 1 && documentHeight > 1) {
            viewOffsetX += (midX - lastMidX) * 2f / documentWidth / viewScale
            viewOffsetY -= (midY - lastMidY) * 2f / documentHeight / viewScale
        }
        val rotationDelta = normalizeAngleDelta(angle - lastAngle)
        viewRotation = normalizeAngle(viewRotation + rotationDelta)

        surface.setViewTransform(viewScale, viewOffsetX, viewOffsetY, viewRotation)
        lastMidX = midX
        lastMidY = midY
        lastDistance = distance
        lastAngle = angle
    }

    private fun updateTransformMetrics(event: MotionEvent) {
        if (event.pointerCount < 2) return
        val index0 = event.findPointerIndex(transformPointerId0)
        val index1 = event.findPointerIndex(transformPointerId1)
        if (index0 < 0 || index1 < 0) return
        lastMidX = (event.getX(index0) + event.getX(index1)) * .5f
        lastMidY = (event.getY(index0) + event.getY(index1)) * .5f
        lastDistance = hypot(
            (event.getX(index1) - event.getX(index0)).toDouble(),
            (event.getY(index1) - event.getY(index0)).toDouble()
        ).toFloat()
        lastAngle = atan2(
            (event.getY(index1) - event.getY(index0)).toDouble(),
            (event.getX(index1) - event.getX(index0)).toDouble()
        ).toFloat()
    }

    private fun viewToDocument(viewX: Float, viewY: Float): Vec2 {
        if (documentWidth <= 1 || documentHeight <= 1 ||
            screenWidth <= 1 || screenHeight <= 1) return Vec2(viewX, viewY)

        // ============================================================
        // [MOD PR-2.3] 与 present.vert 严格互逆：
        //   view.vert:  screenNDC → canvasVirtualNDC → 用户变换 → vTexCoord
        //   逆变换：    view像素 → screenNDC → canvasVirtualNDC → 逆用户变换 → 文档像素
        // ============================================================

        // 1. 屏幕像素 → 屏幕 NDC
        val screenNdcX = viewX / screenWidth * 2f - 1f
        val screenNdcY = 1f - viewY / screenHeight * 2f

        // 2. 屏幕 NDC → canvasVirtualNDC
        val fitScale = computeFitScale()
        val canvasHalfNdcX = documentWidth.toFloat() * fitScale / screenWidth
        val canvasHalfNdcY = documentHeight.toFloat() * fitScale / screenHeight
        val cVx = screenNdcX / canvasHalfNdcX
        val cVy = screenNdcY / canvasHalfNdcY

        // 3. canvasVirtualNDC → p（逆用户变换）
        val qx = cVx / viewScale
        val qy = cVy / viewScale

        val c = cos(viewRotation)
        val s = sin(viewRotation)
        val dx = qx * c - qy * s - viewOffsetX
        val dy = qx * s + qy * c - viewOffsetY

        // 4. p → 文档像素
        val documentX = (dx * .5f + 0.5f) * documentWidth
        val documentY = (1f - (dy * .5f + 0.5f)) * documentHeight

        // 越界诊断
        if (TRACE_XFORM) {
            val oob = documentX < 0f || documentX > documentWidth ||
                    documentY < 0f || documentY > documentHeight
            if (oob) {
                Log.d("XFORM", "OOB view=($viewX,$viewY) doc=($documentX,$documentY) " +
                        "scale=$viewScale rot=$viewRotation off=($viewOffsetX,$viewOffsetY) " +
                        "screen=${screenWidth}x$screenHeight doc=${documentWidth}x$documentHeight fit=$fitScale")
            }
        }

        return Vec2(documentX, documentY)
    }

    // ============================================================
    // [MOD PR-2.3] 与 GLPaintRenderer.computeFitScale 一致的公式
    // ============================================================
    private fun computeFitScale(): Float {
        if (documentWidth <= 0 || documentHeight <= 0 ||
            screenWidth <= 0 || screenHeight <= 0) return 1f
        return minOf(
            screenWidth.toFloat() / documentWidth,
            screenHeight.toFloat() / documentHeight
        )
    }


    private fun normalizeAngleDelta(delta: Float): Float {
        var result = delta
        while (result > PI.toFloat()) result -= 2f * PI.toFloat()
        while (result < -PI.toFloat()) result += 2f * PI.toFloat()
        return result
    }

    private fun normalizeAngle(angle: Float): Float {
        var result = angle
        while (result > PI.toFloat()) result -= 2f * PI.toFloat()
        while (result < -PI.toFloat()) result += 2f * PI.toFloat()
        return result
    }
}