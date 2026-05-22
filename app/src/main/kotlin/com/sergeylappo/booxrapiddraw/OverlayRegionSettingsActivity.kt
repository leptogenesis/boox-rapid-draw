package com.sergeylappo.booxrapiddraw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

const val PREFS_NAME = "overlay_region_prefs"
const val PREF_LEFT = "region_left"
const val PREF_TOP = "region_top"
const val PREF_RIGHT = "region_right"
const val PREF_BOTTOM = "region_bottom"

class OverlayRegionSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // Load saved region or use defaults (top 50px and bottom 10px excluded)
        val left = prefs.getInt(PREF_LEFT, 0)
        val top = prefs.getInt(PREF_TOP, 50)
        val right = prefs.getInt(PREF_RIGHT, screenW)
        val bottom = prefs.getInt(PREF_BOTTOM, screenH - 10)

        val regionView = RegionSelectorView(
            this,
            RectF(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat()
            )
        )

        val saveButton = Button(this).apply {
            text = "Save Region"
            setOnClickListener {
                val r = regionView.getRegion()
                prefs.edit()
                    .putInt(PREF_LEFT, r.left.toInt())
                    .putInt(PREF_TOP, r.top.toInt())
                    .putInt(PREF_RIGHT, r.right.toInt())
                    .putInt(PREF_BOTTOM, r.bottom.toInt())
                    .apply()
                finish()
            }
        }

        val layout = FrameLayout(this).apply {
            addView(
                regionView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                saveButton,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                ).apply { bottomMargin = 20 }
            )
        }

        setContentView(layout)
    }
}

class RegionSelectorView(context: Context, private var region: RectF) : View(context) {

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    private val paint = Paint().apply {
        color = Color.BLUE
        alpha = 80
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }

    private val handleRadius = 30f
    private val edgeSlop = 40f

    // Which part is being dragged: "body", "top", "bottom", "left", "right",
    // "top-left", "top-right", "bottom-left", "bottom-right", or null
    private var dragMode: String? = null
    private var lastX = 0f
    private var lastY = 0f

    fun getRegion() = RectF(region)

    override fun onDraw(canvas: Canvas) {
        // Do NOT call super.onDraw - skip default background rendering

        val dimPaint = Paint().apply {
            color = Color.BLACK
            alpha = 120
            style = Paint.Style.FILL
        }

        // Draw darker dim outside the selected region
        canvas.drawRect(0f, 0f, width.toFloat(), region.top, dimPaint)
        canvas.drawRect(0f, region.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, region.top, region.left, region.bottom, dimPaint)
        canvas.drawRect(region.right, region.top, width.toFloat(), region.bottom, dimPaint)

        // Draw slight dim inside the selected region so it's visually distinct
        val selectedDimPaint = Paint().apply {
            color = Color.WHITE
            alpha = 30
            style = Paint.Style.FILL
        }
        canvas.drawRect(region, selectedDimPaint)

        // Draw region border
        canvas.drawRect(region, borderPaint)

        // Draw corner handles
        listOf(
            region.left to region.top,
            region.right to region.top,
            region.left to region.bottom,
            region.right to region.bottom
        ).forEach { (x, y) ->
            canvas.drawCircle(x, y, handleRadius, handlePaint)
        }

        // Instruction text
        canvas.drawText(
            "Drag edges or corners",
            region.centerX(),
            region.centerY() - 20f,
            textPaint
        )
        canvas.drawText(
            "to set overlay region",
            region.centerX(),
            region.centerY() + 20f,
            textPaint
        )
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) return false
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = when {
                    near(x, region.left) && near(y, region.top) -> "top-left"
                    near(x, region.right) && near(y, region.top) -> "top-right"
                    near(x, region.left) && near(y, region.bottom) -> "bottom-left"
                    near(x, region.right) && near(y, region.bottom) -> "bottom-right"
                    near(y, region.top) && inH(x) -> "top"
                    near(y, region.bottom) && inH(x) -> "bottom"
                    near(x, region.left) && inV(y) -> "left"
                    near(x, region.right) && inV(y) -> "right"
                    region.contains(x, y) -> "body"
                    else -> null
                }
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                when (dragMode) {
                    "body" -> region.offset(dx, dy)
                    "top" -> region.top = (region.top + dy).coerceAtMost(region.bottom - 50)
                    "bottom" -> region.bottom = (region.bottom + dy).coerceAtLeast(region.top + 50)
                    "left" -> region.left = (region.left + dx).coerceAtMost(region.right - 50)
                    "right" -> region.right = (region.right + dx).coerceAtLeast(region.left + 50)
                    "top-left" -> {
                        region.top = (region.top + dy).coerceAtMost(region.bottom - 50)
                        region.left = (region.left + dx).coerceAtMost(region.right - 50)
                    }
                    "top-right" -> {
                        region.top = (region.top + dy).coerceAtMost(region.bottom - 50)
                        region.right = (region.right + dx).coerceAtLeast(region.left + 50)
                    }
                    "bottom-left" -> {
                        region.bottom = (region.bottom + dy).coerceAtLeast(region.top + 50)
                        region.left = (region.left + dx).coerceAtMost(region.right - 50)
                    }
                    "bottom-right" -> {
                        region.bottom = (region.bottom + dy).coerceAtLeast(region.top + 50)
                        region.right = (region.right + dx).coerceAtLeast(region.left + 50)
                    }
                }
                region.left = region.left.coerceAtLeast(0f)
                region.top = region.top.coerceAtLeast(0f)
                region.right = region.right.coerceAtMost(width.toFloat())
                region.bottom = region.bottom.coerceAtMost(height.toFloat())
                lastX = x
                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP -> dragMode = null
        }
        return true // always consume — never pass through to underlying app
    }

    private fun near(a: Float, b: Float) = Math.abs(a - b) < edgeSlop
    private fun inH(x: Float) = x in region.left..region.right
    private fun inV(y: Float) = y in region.top..region.bottom
}
