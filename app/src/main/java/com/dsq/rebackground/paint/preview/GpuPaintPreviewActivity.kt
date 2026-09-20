package com.dsq.rebackground.paint.preview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dsq.rebackground.paint.brush.BrushDefinition
import com.dsq.rebackground.paint.brush.BrushDynamics
import com.dsq.rebackground.paint.brush.BrushGenerator
import com.dsq.rebackground.paint.brush.BrushRuntimeState
import com.dsq.rebackground.paint.input.PointerInputProcessor
import com.dsq.rebackground.paint.rendering.gl.PaintGLSurfaceView
import com.dsq.rebackground.paint.stroke.StrokePoint

/**
 * Isolated P3 diagnostics surface. It proves the new EGL/GLES lifecycle without routing any production PaintActivity
 * input or replacing the legacy canvas.
 */
class GpuPaintPreviewActivity : AppCompatActivity() {
    private lateinit var surface: PaintGLSurfaceView
    private lateinit var inputProcessor: PointerInputProcessor
    private val generator = BrushGenerator()
    private val generatorStates = HashMap<Int, BrushRuntimeState>()
    private var documentWidth = 1
    private var documentHeight = 1
    private var brush = BrushDefinition(
        "preview-round", "Preview round", 24f,
        dynamics = BrushDynamics(
            minimumDiameterRatio = .3f,
            pressureSizeInfluence = 1f,
            speedSizeInfluence = .08f
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = PaintGLSurfaceView(this)
        setContentView(surface)
        inputProcessor = PointerInputProcessor(
            onStrokeFinished = { generatorStates.clear() },
            onStrokeCancelled = { generatorStates.clear() },
            onStrokePoint = ::onStrokePoint
        )
        surface.setOnTouchListener { _, event ->
            inputProcessor.onMotionEvent(event)
            true
        }
        surface.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width > 0 && height > 0 && (width != documentWidth || height != documentHeight)) {
                documentWidth = width
                documentHeight = height
                brush = brush.copy(baseDiameterDocumentUnits = documentWidth * .025f)
                surface.showBrushStamps(emptyList(), documentWidth, documentHeight)
            }
        }
        surface.post {
            documentWidth = surface.width.coerceAtLeast(1)
            documentHeight = surface.height.coerceAtLeast(1)
            brush = brush.copy(baseDiameterDocumentUnits = documentWidth * .025f)
            surface.showBrushStamps(emptyList(), documentWidth, documentHeight)
        }
    }

    private fun onStrokePoint(pointerId: Int, point: StrokePoint) {
        if (documentWidth <= 1 || documentHeight <= 1) return
        val previous = generatorStates[pointerId] ?: BrushRuntimeState()
        val output = generator.generate(brush, point, previous)
        generatorStates[pointerId] = output.nextState
        surface.addBrushStamp(output.stamp, documentWidth, documentHeight)
    }

    override fun onResume() {
        super.onResume()
        surface.onResume()
        surface.renderOnce()
    }

    override fun onPause() {
        surface.onPause()
        super.onPause()
    }
}
