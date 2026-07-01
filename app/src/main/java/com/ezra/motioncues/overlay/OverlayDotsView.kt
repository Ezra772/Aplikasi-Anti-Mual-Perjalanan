package com.ezra.motioncues.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Transparent full-screen overlay view that renders 8 motion-cue dots:
 * 4 on the left edge, 4 on the right edge.
 *
 * Dots are placed at 25 %, 40 %, 60 %, and 75 % of the view height.
 *
 * When the device tilts:
 *   - Left-edge dots shift in the SAME direction as the tilt.
 *   - Right-edge dots shift in the OPPOSITE direction (parallax effect).
 *
 * Landscape behaviour: only the horizontal (X-axis) offset is applied;
 * the vertical (Y-axis) offset is suppressed so no horizontal indicator
 * line appears when the device is rotated sideways.
 *
 * PERFORMANCE CONTRACT — onDraw() allocates ZERO objects:
 *   All Paint, coordinate, and scratch objects are pre-allocated in init.
 *
 * Hardware-accelerated via [LAYER_TYPE_HARDWARE].
 */
class OverlayDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        /** Number of dots on each side. */
        private const val DOTS_PER_SIDE = 4

        /** Fractional vertical positions for each dot (relative to view height). */
        private val DOT_Y_FRACTIONS = floatArrayOf(0.25f, 0.40f, 0.60f, 0.75f)
    }

    // ── Dot appearance properties ────────────────────────────────────────────

    /** ARGB color of the dots (alpha channel is overridden by [dotAlpha]). */
    var dotColor: Int = Color.WHITE
        set(value) {
            field = value
            dotPaint.color = value
            dotPaint.alpha = dotAlpha
            invalidate()
        }

    /** Opacity of the dots in the range [0, 255]. */
    var dotAlpha: Int = 180
        set(value) {
            field = value
            dotPaint.alpha = value
            invalidate()
        }

    /** Radius of each dot in density-independent pixels. */
    var dotRadiusDp: Float = 8f
        set(value) {
            field = value
            dotRadiusPx = value * resources.displayMetrics.density
            invalidate()
        }

    /** Horizontal offset applied to dots (positive = right). */
    var offsetX: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /** Vertical offset applied to dots (positive = down). */
    var offsetY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    // ── Pre-allocated internals ──────────────────────────────────────────────

    /** Paint reused every frame — allocated once in init. */
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Dot radius in physical pixels (derived from [dotRadiusDp]). */
    private var dotRadiusPx: Float = 8f * context.resources.displayMetrics.density

    /** Cached screen half-width — updated in onSizeChanged. */
    private var viewWidth: Float = 0f

    /** Cached screen height — updated in onSizeChanged. */
    private var viewHeight: Float = 0f

    // ── Init ─────────────────────────────────────────────────────────────────

    init {
        // Hardware layer: compositing is offloaded to GPU
        setLayerType(LAYER_TYPE_HARDWARE, null)

        dotPaint.color = dotColor
        dotPaint.alpha = dotAlpha
        dotPaint.style = Paint.Style.FILL
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        viewHeight = h.toFloat()
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    /**
     * Draws 8 dots with zero heap allocation.
     *
     * Left dots: edge is the view left margin (x ≈ dotRadius); offset is ADDED.
     * Right dots: edge is the view right margin (x ≈ width − dotRadius); offset is SUBTRACTED
     *             (parallax: they move opposite to left dots).
     */
    override fun onDraw(canvas: Canvas) {
        // No super.onDraw — background is transparent
        if (viewWidth == 0f || viewHeight == 0f) return

        val leftEdgeX = dotRadiusPx
        val rightEdgeX = viewWidth - dotRadiusPx
        val minY = dotRadiusPx
        val maxY = viewHeight - dotRadiusPx

        for (i in 0 until DOTS_PER_SIDE) {
            val baseCy = viewHeight * DOT_Y_FRACTIONS[i]

            // Clamp all positions inside view bounds so dots never disappear
            // off-screen in any orientation (portrait or landscape).
            // coerceIn() on Float is inlined — zero heap allocation.
            val leftCx = (leftEdgeX + offsetX).coerceIn(dotRadiusPx, viewWidth - dotRadiusPx)
            val leftCy = (baseCy + offsetY).coerceIn(minY, maxY)
            canvas.drawCircle(leftCx, leftCy, dotRadiusPx, dotPaint)

            // Right dot: X offset inverted for parallax; Y same direction
            val rightCx = (rightEdgeX - offsetX).coerceIn(dotRadiusPx, viewWidth - dotRadiusPx)
            val rightCy = (baseCy + offsetY).coerceIn(minY, maxY)
            canvas.drawCircle(rightCx, rightCy, dotRadiusPx, dotPaint)
        }
    }

}
