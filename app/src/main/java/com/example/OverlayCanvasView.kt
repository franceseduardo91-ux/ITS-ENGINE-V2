package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View

class OverlayCanvasView(context: Context, private val service: OverlayService) : View(context) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ballStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    // Positions of pool table components (can be manually adjusted by drag or simulated)
    private var cueBallPos = PointF(300f, 800f)
    private var objectBallPos = PointF(600f, 500f)
    private val pockets = listOf(
        PointF(100f, 200f), PointF(540f, 190f), PointF(980f, 200f),
        PointF(100f, 1400f), PointF(540f, 1410f), PointF(980f, 1400f)
    )

    private var animationTick = 0f
    private var selectedPoint: PointF? = null
    private val touchProgressRadius = 80f // Interactive hit radius of the balls

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
    }

    fun updatePhysicsTick() {
        if (!service.isInteractiveMode.value) {
            animationTick += 0.05f
            if (animationTick > 1.0f) {
                animationTick = 0f
            }
            // Reposition for simulated dynamic visualization when not in interactive positioning mode
            cueBallPos.x = 300f + (30f * kotlin.math.sin(animationTick * 2 * Math.PI)).toFloat()
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!service.isInteractiveMode.value) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                selectedPoint = when {
                    distance(x, y, cueBallPos.x, cueBallPos.y) < touchProgressRadius -> cueBallPos
                    distance(x, y, objectBallPos.x, objectBallPos.y) < touchProgressRadius -> objectBallPos
                    else -> {
                        var closestPocket: PointF? = null
                        var minDist = touchProgressRadius
                        pockets.forEach { pocket ->
                            val d = distance(x, y, pocket.x, pocket.y)
                            if (d < minDist) {
                                minDist = d
                                closestPocket = pocket
                            }
                        }
                        closestPocket
                    }
                }
                return selectedPoint != null
            }
            MotionEvent.ACTION_MOVE -> {
                selectedPoint?.let { point ->
                    point.x = x
                    point.y = y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedPoint = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val overlayColorHex = try {
            service.colorLine.value.value.toInt()
        } catch (e: Exception) {
            Color.YELLOW
        }
        linePaint.color = overlayColorHex
        dashedPaint.color = overlayColorHex

        // Find the closest pocket to the object ball
        var targetPocket = pockets[1] // Default to top-center pocket
        var minDist = Float.MAX_VALUE
        pockets.forEach { pocket ->
            val d = distance(objectBallPos.x, objectBallPos.y, pocket.x, pocket.y)
            if (d < minDist) {
                minDist = d
                targetPocket = pocket
            }
        }

        val ballRadius = 24f

        // Calculate Ghost Ball position (contact point to punch Object Ball into the pocket)
        val dxVec = objectBallPos.x - targetPocket.x
        val dyVec = objectBallPos.y - targetPocket.y
        val distPocketToBall = kotlin.math.sqrt(dxVec * dxVec + dyVec * dyVec)

        val ghostBallX: Float
        val ghostBallY: Float
        if (distPocketToBall > 0) {
            // Place ghost ball exactly opposite to pocket, contact point is one ball diameter (2 * ballRadius) away
            ghostBallX = objectBallPos.x + (dxVec / distPocketToBall) * (2 * ballRadius)
            ghostBallY = objectBallPos.y + (dyVec / distPocketToBall) * (2 * ballRadius)
        } else {
            ghostBallX = objectBallPos.x
            ghostBallY = objectBallPos.y
        }

        // 1. ESP Cue Stick Alignment line (back-extension of cue ball)
        if (service.espLineCueEnabled.value) {
            val dxCue = ghostBallX - cueBallPos.x
            val dyCue = ghostBallY - cueBallPos.y
            val distCue = kotlin.math.sqrt(dxCue * dxCue + dyCue * dyCue)
            if (distCue > 0) {
                // Draw stick pointing from behind the cue ball
                val startCueX = cueBallPos.x - (dxCue / distCue) * 200f
                val startCueY = cueBallPos.y - (dyCue / distCue) * 200f
                val endCueX = cueBallPos.x - (dxCue / distCue) * 40f
                val endCueY = cueBallPos.y - (dyCue / distCue) * 40f
                linePaint.strokeWidth = 10f
                canvas.drawLine(startCueX, startCueY, endCueX, endCueY, linePaint)
                linePaint.strokeWidth = 6f
            }
        }

        // 2. Aim Cue Ball Path to Ghost Ball
        if (service.aimBallEnabled.value) {
            // Draw predictive path from cue ball center to ghost ball center
            canvas.drawLine(cueBallPos.x, cueBallPos.y, ghostBallX, ghostBallY, dashedPaint)
            
            // Render Ghost Ball shadow at point of impact
            ballPaint.color = Color.argb(130, 255, 255, 255) // White semi-translucent
            canvas.drawCircle(ghostBallX, ghostBallY, ballRadius, ballPaint)
            canvas.drawCircle(ghostBallX, ghostBallY, ballRadius, ballStrokePaint)

            // Draw alignment helper details
            ballPaint.color = Color.argb(180, 255, 215, 0) // Gold
            canvas.drawCircle(ghostBallX, ghostBallY, 6f, ballPaint)
        }

        // 3. Object Ball trajectory to Target Pocket
        if (service.espLineEnabled.value) {
            // From Object Ball center directly to Target Pocket
            canvas.drawLine(objectBallPos.x, objectBallPos.y, targetPocket.x, targetPocket.y, linePaint)

            // Draw a dashed projection showing potential cue bounce/deflection trajectory
            val dxCue = ghostBallX - cueBallPos.x
            val dyCue = ghostBallY - cueBallPos.y
            val distCue = kotlin.math.sqrt(dxCue * dxCue + dyCue * dyCue)

            if (distCue > 0 && distPocketToBall > 0) {
                // Object Ball direction vector
                val objDirX = -dxVec / distPocketToBall
                val objDirY = -dyVec / distPocketToBall

                // Cue Ball deflection vector is perpendicular to the Object Ball's path
                var sign = if (dxCue * objDirY - dyCue * objDirX > 0) 1f else -1f
                val cueDeflectX = -objDirY * sign
                val cueDeflectY = objDirX * sign

                val deflectEndColX = ghostBallX + cueDeflectX * 150f
                val deflectEndColY = ghostBallY + cueDeflectY * 150f

                // Draw cue ball deflection (tangent) line
                val origDashedColor = dashedPaint.color
                dashedPaint.color = Color.LTGRAY
                canvas.drawLine(ghostBallX, ghostBallY, deflectEndColX, deflectEndColY, dashedPaint)
                dashedPaint.color = origDashedColor
            }
        }

        // 4. Highlight Balls (Cue ball + Target ball)
        if (service.colorBalls.value) {
            // Highlight cue ball in bright white with golden accent glow
            ballPaint.color = Color.WHITE
            canvas.drawCircle(cueBallPos.x, cueBallPos.y, ballRadius, ballPaint)
            canvas.drawCircle(cueBallPos.x, cueBallPos.y, ballRadius, ballStrokePaint)
            
            // Glow effect
            ballPaint.color = Color.argb(80, 255, 215, 0)
            canvas.drawCircle(cueBallPos.x, cueBallPos.y, ballRadius + 8f, ballPaint)

            // Highlight target object ball in vibrant Crimson with a glow
            ballPaint.color = Color.argb(255, 220, 20, 60)
            canvas.drawCircle(objectBallPos.x, objectBallPos.y, ballRadius, ballPaint)
            canvas.drawCircle(objectBallPos.x, objectBallPos.y, ballRadius, ballStrokePaint)
            
            // Crimson Glow
            ballPaint.color = Color.argb(80, 220, 20, 60)
            canvas.drawCircle(objectBallPos.x, objectBallPos.y, ballRadius + 8f, ballPaint)
        }

        // 5. Pocket Highlighting Assistance
        if (service.aimCacapaEnabled.value) {
            pockets.forEach { pocket ->
                val isSelectedPocket = (pocket == targetPocket)
                if (isSelectedPocket) {
                    ballPaint.color = Color.argb(120, 0, 255, 0) // Target Pocket glows in green!
                    canvas.drawCircle(pocket.x, pocket.y, 48f, ballPaint)
                    
                    val activeStroke = Paint(ballStrokePaint).apply {
                        color = Color.GREEN
                        strokeWidth = 6f
                    }
                    canvas.drawCircle(pocket.x, pocket.y, 48f, activeStroke)
                } else {
                    ballPaint.color = Color.argb(60, 255, 215, 0) // Other pockets glow in Gold
                    canvas.drawCircle(pocket.x, pocket.y, 40f, ballPaint)
                    canvas.drawCircle(pocket.x, pocket.y, 40f, ballStrokePaint)
                }
            }
        }

        // 6. Visual alignment instructions text in Positioning Mode
        if (service.isInteractiveMode.value) {
            textPaint.color = Color.GREEN
            textPaint.textSize = 34f
            canvas.drawText("ALIGN MODE ACTIVE: DRAG BALLS / POCKETS ON THE SCREEN", width / 2f, 120f, textPaint)
            textPaint.color = Color.YELLOW
            textPaint.textSize = 28f
            canvas.drawText("Align elements on top of your pool game, then disable align mode to lock lines.", width / 2f, 154f, textPaint)
        }
    }
}
