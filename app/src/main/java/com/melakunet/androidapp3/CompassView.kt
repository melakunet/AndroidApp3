package com.melakunet.androidapp3

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Compass dial that rotates to show real north and points to home.
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var azimuth = 0f
    private var homeAngle: Float? = null
    private var isAtHome = false

    // Theme colors
    private val colorSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
    private val colorOnSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    private val colorPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
    private val colorNorth = Color.parseColor("#E53935") // Red
    private val colorHome = Color.parseColor("#43A047") // Green
    private val colorSouth = Color.GRAY

    private val dialFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorSurface
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorOnSurface
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorOnSurface
        style = Paint.Style.STROKE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorOnSurface
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val degreePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorOnSurface
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimary
        style = Paint.Style.FILL
        alpha = 60 // Light fill
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorNorth
        style = Paint.Style.FILL
    }

    private val homePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorHome
        style = Paint.Style.FILL
    }

    private val path = Path()

    // Set rotation and redraw.
    fun setAzimuth(degrees: Float) {
        azimuth = degrees
        invalidate()
    }

    // Set the angle to home and if we are already there.
    fun setHomeAngle(degrees: Float?, atHome: Boolean = false) {
        homeAngle = degrees
        isAtHome = atHome
        invalidate()
    }

    // Keep the compass square no matter how it is sized.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
                       getDefaultSize(suggestedMinimumHeight, heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    // Draw the dial, needles and home indicator.
    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(centerX, centerY) * 0.85f

        // 1. Draw solid dial face
        canvas.drawCircle(centerX, centerY, radius, dialFacePaint)
        canvas.drawCircle(centerX, centerY, radius, ringPaint)

        // 2. Draw rotating dial
        canvas.save()
        canvas.rotate(-azimuth, centerX, centerY)

        drawStarRose(canvas, centerX, centerY, radius * 0.4f)
        drawTicksAndDegrees(canvas, centerX, centerY, radius)
        drawCardinalLetters(canvas, centerX, centerY, radius)
        drawNeedles(canvas, centerX, centerY, radius)

        canvas.restore()

        // 3. Draw fixed elements
        drawHeadingMarker(canvas, centerX, centerY, radius)
        drawHomeIndicator(canvas, centerX, centerY, radius)
    }

    // Draw the star shape behind the main compass needle.
    private fun drawStarRose(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        for (i in 0 until 4) {
            val angle = Math.toRadians(i * 90.0 - 90)
            val tipX = cx + r * cos(angle).toFloat()
            val tipY = cy + r * sin(angle).toFloat()
            
            val leftAngle = Math.toRadians(i * 90.0 - 135)
            val leftX = cx + (r * 0.3f) * cos(leftAngle).toFloat()
            val leftY = cy + (r * 0.3f) * sin(leftAngle).toFloat()
            
            if (i == 0) path.moveTo(tipX, tipY) else path.lineTo(tipX, tipY)
            path.lineTo(leftX, leftY)
        }
        path.close()
        canvas.drawPath(path, starPaint)
        
        starPaint.style = Paint.Style.STROKE
        starPaint.strokeWidth = 2f
        starPaint.alpha = 255
        canvas.drawPath(path, starPaint)
        starPaint.style = Paint.Style.FILL
        starPaint.alpha = 60
    }

    // Draw the ring ticks and degree numbers around the dial.
    private fun drawTicksAndDegrees(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        for (i in 0 until 360 step 5) {
            val angleRad = Math.toRadians(i.toDouble() - 90)
            val sX = cx + r * cos(angleRad).toFloat()
            val sY = cy + r * sin(angleRad).toFloat()
            
            val tickLen = when {
                i % 30 == 0 -> 40f
                i % 15 == 0 -> 25f
                else -> 15f
            }
            tickPaint.strokeWidth = if (i % 30 == 0) 4f else 2f
            
            val eX = cx + (r - tickLen) * cos(angleRad).toFloat()
            val eY = cy + (r - tickLen) * sin(angleRad).toFloat()
            canvas.drawLine(sX, sY, eX, eY, tickPaint)

            // Degrees every 30
            if (i % 30 == 0) {
                canvas.save()
                canvas.rotate(i.toFloat(), cx, cy)
                canvas.drawText(i.toString(), cx, cy - r + 70f, degreePaint)
                canvas.restore()
            }
        }
    }

    // Draw the N, E, S and W labels around the dial.
    private fun drawCardinalLetters(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val cardinals = mapOf(
            0 to "N", 90 to "E", 180 to "S", 270 to "W",
            45 to "NE", 135 to "SE", 225 to "SW", 315 to "NW"
        )
        for ((deg, label) in cardinals) {
            val angleRad = Math.toRadians(deg.toDouble() - 90)
            val dist = if (label.length == 1) r - 110f else r - 100f
            labelPaint.textSize = if (label.length == 1) 48f else 32f
            labelPaint.color = if (deg == 0) colorNorth else colorOnSurface
            
            val x = cx + dist * cos(angleRad).toFloat()
            val y = cy + dist * sin(angleRad).toFloat() - (labelPaint.descent() + labelPaint.ascent()) / 2
            canvas.drawText(label, x, y, labelPaint)
        }
    }

    // Draw the fixed top marker the rotating dial moves under.
    private fun drawHeadingMarker(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx, cy - r - 10)
        path.lineTo(cx - 15, cy - r - 40)
        path.lineTo(cx + 15, cy - r - 40)
        path.close()
        canvas.drawPath(path, markerPaint)
    }

    // Draw the red north needle and gray south needle.
    private fun drawNeedles(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val needleLen = r * 0.7f
        val needleWidth = 20f

        // Draw the north and south needle halves
        
        // North half
        path.reset()
        path.moveTo(cx, cy - needleLen)
        path.lineTo(cx - needleWidth, cy)
        path.lineTo(cx + needleWidth, cy)
        path.close()
        needlePaint.color = colorNorth
        canvas.drawPath(path, needlePaint)

        // South half
        path.reset()
        path.moveTo(cx, cy + needleLen)
        path.lineTo(cx - needleWidth, cy)
        path.lineTo(cx + needleWidth, cy)
        path.close()
        needlePaint.color = colorSouth
        canvas.drawPath(path, needlePaint)
        
        // Center pin
        needlePaint.color = colorOnSurface
        canvas.drawCircle(cx, cy, 8f, needlePaint)
    }

    // Draw the green home arrow or arrival ring.
    private fun drawHomeIndicator(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        if (homeAngle == null) return

        if (isAtHome) {
            val oldStyle = ringPaint.style
            val oldWidth = ringPaint.strokeWidth
            ringPaint.style = Paint.Style.STROKE
            ringPaint.strokeWidth = 12f
            ringPaint.color = colorHome
            canvas.drawCircle(cx, cy, r + 20, ringPaint)
            ringPaint.style = oldStyle
            ringPaint.strokeWidth = oldWidth
            ringPaint.color = colorOnSurface
            return
        }

        canvas.save()
        canvas.rotate(homeAngle!!, cx, cy)
        
        // Arrow
        path.reset()
        val arrowY = cy - r - 30
        path.moveTo(cx, arrowY - 30)
        path.lineTo(cx - 20, arrowY + 10)
        path.lineTo(cx + 20, arrowY + 10)
        path.close()
        canvas.drawPath(path, homePaint)
        
        // "HOME" label
        labelPaint.textSize = 20f
        labelPaint.color = colorHome
        canvas.drawText("HOME", cx, arrowY - 40, labelPaint)
        
        canvas.restore()
    }
}
