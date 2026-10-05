package com.autel.sdk.debugtools.fragment

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.autel.drone.sdk.vmodelx.manager.keyvalue.value.aiservice.enums.DetectTargetEnum
import com.autel.player.player.ai.AIInfo
import kotlin.math.min

/**
 * AI detection target drawing delegate, responsible for:
 * - Managing creation and attachment of [AIDetectionOverlay]
 * - Converting normalized coordinates in [AIInfo] to view pixel coordinates
 * - Maintaining the target type name mapping
 *
 * The Fragment only holds this delegate and interacts via [attach] / [processAIInfo] / [clear] / [detach].
 */
class AIDetectionOverlayDelegate {

    private var overlay: AIDetectionOverlay? = null

    // ======================== Public API ========================

    /**
     * Create the Overlay and add it to [parent].
     */
    fun attach(context: Context, parent: ViewGroup) {
        if (overlay != null) return
        overlay = AIDetectionOverlay(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        parent.addView(overlay)
    }

    /**
     * Remove the Overlay from the parent and release references.
     */
    fun detach() {
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay = null
    }

    /**
     * Clear drawn targets.
     */
    fun clear() {
        overlay?.clearTargets()
    }

    /**
     * Parse [AIInfo] and update the Overlay drawing.
     * @return parsed target list (usable for external text display), or null if the Overlay is not ready.
     */
    fun processAIInfo(aiInfo: AIInfo): List<DetectedTarget>? {
        val ov = overlay ?: return null
        val viewWidth = ov.width
        val viewHeight = ov.height
        if (viewWidth <= 0 || viewHeight <= 0) return null

        val originWidth = aiInfo.width
        val originHeight = aiInfo.height
        val targets = mutableListOf<DetectedTarget>()

        aiInfo.nodes?.forEach { node ->
            val leftRatio = transOriginToCrop(node.x, originWidth, originHeight, viewWidth, viewHeight, isX = true)
            val topRatio = transOriginToCrop(node.y, originWidth, originHeight, viewWidth, viewHeight, isX = false)
            val rightRatio = transOriginToCrop(node.x + node.w, originWidth, originHeight, viewWidth, viewHeight, isX = true)
            val bottomRatio = transOriginToCrop(node.y + node.h, originWidth, originHeight, viewWidth, viewHeight, isX = false)

            val left = (leftRatio * viewWidth).coerceIn(0f, viewWidth.toFloat())
            val top = (topRatio * viewHeight).coerceIn(0f, viewHeight.toFloat())
            val right = (rightRatio * viewWidth).coerceIn(0f, viewWidth.toFloat())
            val bottom = (bottomRatio * viewHeight).coerceIn(0f, viewHeight.toFloat())

            if (right > left && bottom > top) {
                targets.add(
                    DetectedTarget(
                        rect = RectF(left, top, right, bottom),
                        type = node.type,
                        typeName = getTargetTypeName(node.type),
                        dangerLevel = node.dangerLevel
                    )
                )
            }
        }

        ov.updateTargets(targets)
        return targets
    }

    // ======================== Coordinate conversion ========================

    /**
     * Convert normalized coordinates of the raw frame to those of the cropped view (CenterCrop semantics).
     */
    private fun transOriginToCrop(
        originPos: Float,
        originWidth: Int, originHeight: Int,
        viewWidth: Int, viewHeight: Int,
        isX: Boolean
    ): Float {
        if (originWidth <= 0 || originHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return originPos

        val originAspect = originWidth.toFloat() / originHeight
        val viewAspect = viewWidth.toFloat() / viewHeight

        return if (isX) {
            if (viewAspect >= originAspect) {
                originPos
            } else {
                val visibleRatio = viewAspect / originAspect
                val offset = (1f - visibleRatio) / 2f
                (originPos - offset) / visibleRatio
            }
        } else {
            if (viewAspect <= originAspect) {
                originPos
            } else {
                val visibleRatio = originAspect / viewAspect
                val offset = (1f - visibleRatio) / 2f
                (originPos - offset) / visibleRatio
            }
        }
    }

    // ======================== Target type name mapping ========================

    private fun getTargetTypeName(type: Int): String {
        val enum = DetectTargetEnum.findEnum(type)
        if (enum != DetectTargetEnum.UNKNOWN) {
            return DetectTargetEnum.getEnglishName(enum).ifEmpty { enum.info }
        }
        return "Unknown($type)"
    }

    // ======================== Data structures ========================

    data class DetectedTarget(
        val rect: RectF,
        val type: Int,
        val typeName: String,
        val dangerLevel: Int
    )

    // ======================== Overlay View ========================

    class AIDetectionOverlay @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
    ) : View(context, attrs) {

        private val targets = mutableListOf<DetectedTarget>()
        private var lastRefreshTime = 0L

        private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GREEN
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GREEN
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GREEN
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }

        fun updateTargets(newTargets: List<DetectedTarget>) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRefreshTime < 30) return
            lastRefreshTime = now
            targets.clear()
            targets.addAll(newTargets)
            postInvalidate()
        }

        fun clearTargets() {
            targets.clear()
            postInvalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for (target in targets) {
                drawTarget(canvas, target)
            }
        }

        private fun drawTarget(canvas: Canvas, target: DetectedTarget) {
            val r = target.rect

            boxPaint.color = COLOR_GREEN
            canvas.drawRect(r, boxPaint)

            val cornerLen = min(20f, min(r.width(), r.height()) / 3f)
            cornerPaint.color = COLOR_GREEN
            drawCorners(canvas, r, cornerLen)

            drawLabel(canvas, r, target.typeName)
        }

        private fun drawCorners(canvas: Canvas, r: RectF, len: Float) {
            canvas.drawLine(r.left, r.top, r.left + len, r.top, cornerPaint)
            canvas.drawLine(r.left, r.top, r.left, r.top + len, cornerPaint)

            canvas.drawLine(r.right, r.top, r.right - len, r.top, cornerPaint)
            canvas.drawLine(r.right, r.top, r.right, r.top + len, cornerPaint)

            canvas.drawLine(r.left, r.bottom, r.left + len, r.bottom, cornerPaint)
            canvas.drawLine(r.left, r.bottom, r.left, r.bottom - len, cornerPaint)

            canvas.drawLine(r.right, r.bottom, r.right - len, r.bottom, cornerPaint)
            canvas.drawLine(r.right, r.bottom, r.right, r.bottom - len, cornerPaint)
        }

        private fun drawLabel(canvas: Canvas, boxRect: RectF, text: String) {
            val padding = 10f
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.descent() - textPaint.ascent()
            val labelWidth = textWidth + padding * 2
            val labelHeight = textHeight + padding * 2

            var labelLeft = boxRect.left
            var labelBottom = boxRect.top
            var labelTop = labelBottom - labelHeight

            if (labelTop < 0f) {
                labelTop = boxRect.bottom
                labelBottom = labelTop + labelHeight
            }

            val labelRight = min(labelLeft + labelWidth, width.toFloat())
            if (labelRight == width.toFloat()) {
                labelLeft = labelRight - labelWidth
            }

            val labelRect = RectF(labelLeft, labelTop, labelRight, labelBottom)
            labelBgPaint.color = COLOR_GREEN
            canvas.drawRoundRect(labelRect, 6f, 6f, labelBgPaint)

            val textX = labelLeft + padding
            val textY = labelTop + padding - textPaint.ascent()
            canvas.drawText(text, textX, textY, textPaint)
        }

        companion object {
            private const val COLOR_GREEN = 0xFFABFF2A.toInt()
        }
    }
}
