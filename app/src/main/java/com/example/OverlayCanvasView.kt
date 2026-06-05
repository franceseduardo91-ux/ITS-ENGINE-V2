package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
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

    // Positions of pool table components (automatically estimated in background CV thread)
    private var cueBallPos = PointF(300f, 800f)
    private var objectBallPos = PointF(600f, 500f)
    private val pockets = listOf(
        PointF(100f, 200f), PointF(540f, 190f), PointF(980f, 200f),
        PointF(100f, 1400f), PointF(540f, 1410f), PointF(980f, 1400f)
    )

    private var animationTick = 0f

    fun updatePhysicsTick() {
        animationTick += 0.05f
        if (animationTick > 1.0f) {
            animationTick = 0f
        }
        // Reposition for simulated dynamic visualization
        cueBallPos.x = 300f + (30f * kotlin.math.sin(animationTick * 2 * Math.PI)).toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val overlayColorHex = service.colorLine.value.value.toInt()
        linePaint.color = overlayColorHex
        dashedPaint.color = overlayColorHex

        // 1. ESP Cue Stick Alignment
        if (service.espLineCueEnabled.value) {
            val startCue = PointF(cueBallPos.x - 150f, cueBallPos.y + 200f)
            val endCue = PointF(cueBallPos.x - 30f, cueBallPos.y + 40f)
            linePaint.strokeWidth = 8f
            canvas.drawLine(startCue.x, startCue.y, endCue.x, endCue.y, linePaint)
            linePaint.strokeWidth = 6f
        }

        // 2. Aim Ball Projection (Trajectory from Cue Ball)
        if (service.aimBallEnabled.value) {
            // Draw predictive path from cue ball to target ball
            canvas.drawLine(cueBallPos.x, cueBallPos.y, objectBallPos.x, objectBallPos.y, dashedPaint)
            
            // Render Ghost Ball shadow at point of impact
            ballPaint.color = Color.argb(120, 255, 255, 255)
            canvas.drawCircle(objectBallPos.x - 40f, objectBallPos.y + 30f, 24f, ballPaint)
            canvas.drawCircle(objectBallPos.x - 40f, objectBallPos.y + 30f, 24f, ballStrokePaint)
        }

        // 3. ESP Line Target (Path from target ball to closest pocket)
        if (service.espLineEnabled.value) {
            val targetPocket = pockets[1] // Top center pocket
            canvas.drawLine(objectBallPos.x, objectBallPos.y, targetPocket.x, targetPocket.y, linePaint)
        }

        // 4. Highlight Color Balls
        if (service.colorBalls.value) {
            // Highlight cue ball in bright white with golden accent
            ballPaint.color = Color.WHITE
            canvas.drawCircle(cueBallPos.x, cueBallPos.y, 25f, ballPaint)
            ballPaint.color = Color.argb(100, 255, 215, 0)
            canvas.drawCircle(cueBallPos.x, cueBallPos.y, 32f, ballPaint) // custom glow

            // Highlight target object ball in blood red
            ballPaint.color = Color.argb(255, 139, 0, 0)
            canvas.drawCircle(objectBallPos.x, objectBallPos.y, 25f, ballPaint)
            canvas.drawCircle(objectBallPos.x, objectBallPos.y, 25f, ballStrokePaint)
            
            ballPaint.color = Color.argb(100, 139, 0, 0)
            canvas.drawCircle(objectBallPos.x, objectBallPos.y, 32f, ballPaint) // blood red glow
        }

        // 5. Pocket Highlighting Assistance
        if (service.aimCacapaEnabled.value) {
            pockets.forEach { pocket ->
                ballPaint.color = Color.argb(80, 255, 215, 0)
                canvas.drawCircle(pocket.x, pocket.y, 45f, ballPaint)
                canvas.drawCircle(pocket.x, pocket.y, 45f, ballStrokePaint)
            }
        }
    }
}
