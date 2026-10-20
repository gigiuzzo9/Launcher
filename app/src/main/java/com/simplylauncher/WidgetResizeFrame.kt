package com.simplylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class WidgetResizeFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }

    private val handleRadiusPx = 12f * resources.displayMetrics.density
    private val handleTouchRadiusPx = 32f * resources.displayMetrics.density

    private var frameLeft = 0
    private var frameTop = 0
    private var frameRight = 0
    private var frameBottom = 0

    private var cellLeft = 0
    private var cellTop = 0
    private var cellRight = 0
    private var cellBottom = 0

    private var cellWidthPx = 0
    private var cellHeightPx = 0
    private var minCellsWidth = 1
    private var minCellsHeight = 1
    private var maxCellsWidth = 4
    private var maxCellsHeight = 6
    private var gridOffsetX = 0
    private var gridOffsetY = 0

    private var activeHandle = HANDLE_NONE
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var startCellLeft = 0
    private var startCellTop = 0
    private var startCellRight = 0
    private var startCellBottom = 0

    var onResizeEnd: ((left: Int, top: Int, right: Int, bottom: Int) -> Unit)? = null
    var onResizeCancel: (() -> Unit)? = null

    companion object {
        private const val HANDLE_NONE = 0
        private const val HANDLE_LEFT = 1
        private const val HANDLE_TOP = 2
        private const val HANDLE_RIGHT = 3
        private const val HANDLE_BOTTOM = 4
    }

    fun configure(
        frameLeft: Int, frameTop: Int, frameRight: Int, frameBottom: Int,
        cellLeft: Int, cellTop: Int, cellRight: Int, cellBottom: Int,
        cellWidthPx: Int, cellHeightPx: Int,
        minCellsWidth: Int, minCellsHeight: Int,
        maxCellsWidth: Int, maxCellsHeight: Int,
        gridOffsetX: Int, gridOffsetY: Int
    ) {
        this.frameLeft = frameLeft
        this.frameTop = frameTop
        this.frameRight = frameRight
        this.frameBottom = frameBottom
        this.cellLeft = cellLeft
        this.cellTop = cellTop
        this.cellRight = cellRight
        this.cellBottom = cellBottom
        this.cellWidthPx = cellWidthPx
        this.cellHeightPx = cellHeightPx
        this.minCellsWidth = minCellsWidth
        this.minCellsHeight = minCellsHeight
        this.maxCellsWidth = maxCellsWidth
        this.maxCellsHeight = maxCellsHeight
        this.gridOffsetX = gridOffsetX
        this.gridOffsetY = gridOffsetY
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = detectHandle(event.x, event.y)
                if (activeHandle == HANDLE_NONE) {
                    onResizeCancel?.invoke()
                    return false
                }
                startTouchX = event.x
                startTouchY = event.y
                startCellLeft = cellLeft
                startCellTop = cellTop
                startCellRight = cellRight
                startCellBottom = cellBottom
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle == HANDLE_NONE) return false
                val dxCells = ((event.x - startTouchX) / cellWidthPx).toInt()
                val dyCells = ((event.y - startTouchY) / cellHeightPx).toInt()

                when (activeHandle) {
                    HANDLE_LEFT -> {
                        val newLeft = (startCellLeft + dxCells).coerceIn(
                            maxOf(0, startCellRight - maxCellsWidth + 1),
                            startCellRight - minCellsWidth + 1
                        )
                        cellLeft = newLeft
                    }
                    HANDLE_TOP -> {
                        val newTop = (startCellTop + dyCells).coerceIn(
                            maxOf(0, startCellBottom - maxCellsHeight + 1),
                            startCellBottom - minCellsHeight + 1
                        )
                        cellTop = newTop
                    }
                    HANDLE_RIGHT -> {
                        val newRight = (startCellRight + dxCells).coerceIn(
                            startCellLeft + minCellsWidth - 1,
                            minOf(startCellLeft + maxCellsWidth - 1, maxCellsWidth - 1)
                        )
                        cellRight = newRight
                    }
                    HANDLE_BOTTOM -> {
                        val newBottom = (startCellBottom + dyCells).coerceIn(
                            startCellTop + minCellsHeight - 1,
                            minOf(startCellTop + maxCellsHeight - 1, maxCellsHeight - 1)
                        )
                        cellBottom = newBottom
                    }
                }

                // Aggiorna i bordi visivi in base alle nuove celle
                frameLeft = gridOffsetX + cellLeft * cellWidthPx
                frameTop = gridOffsetY + cellTop * cellHeightPx
                frameRight = gridOffsetX + (cellRight + 1) * cellWidthPx
                frameBottom = gridOffsetY + (cellBottom + 1) * cellHeightPx
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeHandle != HANDLE_NONE) {
                    onResizeEnd?.invoke(cellLeft, cellTop, cellRight, cellBottom)
                    activeHandle = HANDLE_NONE
                }
                return true
            }
        }
        return false
    }

    private fun detectHandle(x: Float, y: Float): Int {
        val w = frameRight - frameLeft
        val h = frameBottom - frameTop

        if (abs(x - 0f) < handleTouchRadiusPx && y > 0 && y < h) return HANDLE_LEFT
        if (abs(x - w) < handleTouchRadiusPx && y > 0 && y < h) return HANDLE_RIGHT
        if (abs(y - 0f) < handleTouchRadiusPx && x > 0 && x < w) return HANDLE_TOP
        if (abs(y - h) < handleTouchRadiusPx && x > 0 && x < w) return HANDLE_BOTTOM

        return HANDLE_NONE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(
            frameLeft.toFloat() + handleRadiusPx,
            frameTop.toFloat() + handleRadiusPx,
            frameRight.toFloat() - handleRadiusPx,
            frameBottom.toFloat() - handleRadiusPx,
            borderPaint
        )

        val cx = (frameLeft + frameRight) / 2f
        val cy = (frameTop + frameBottom) / 2f

        canvas.drawCircle(frameLeft.toFloat(), cy, handleRadiusPx, handlePaint)
        canvas.drawCircle(frameRight.toFloat(), cy, handleRadiusPx, handlePaint)
        canvas.drawCircle(cx, frameTop.toFloat(), handleRadiusPx, handlePaint)
        canvas.drawCircle(cx, frameBottom.toFloat(), handleRadiusPx, handlePaint)
    }
}
