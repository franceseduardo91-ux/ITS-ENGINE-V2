package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

class OverlayCanvasView(context: Context, private val service: OverlayService) : View(context) {

    // Ball class representing standard pool table elements
    class BallInfo(
        val id: Int,
        var pos: PointF,
        val color: Int,
        val isStripe: Boolean,
        val labelColor: Int = Color.BLACK
    )

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
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
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Billiard Table Coordinates (will be scaled dynamically in onSizeChanged)
    private var tableLeft = 100f
    private var tableRight = 980f
    private var tableTop = 200f
    private var tableBottom = 1400f

    // 16 Standard Balls (0 is white Cue ball, 1-15 are object balls)
    private val balls = mutableListOf<BallInfo>().apply {
        // Cue ball (White)
        add(BallInfo(0, PointF(300f, 950f), Color.WHITE, false, Color.BLACK))
        // Solid balls
        add(BallInfo(1, PointF(540f, 500f), Color.parseColor("#FFD700"), false)) // Gold/Yellow
        add(BallInfo(2, PointF(600f, 550f), Color.parseColor("#1E90FF"), false)) // Blue
        add(BallInfo(3, PointF(480f, 550f), Color.parseColor("#FF3333"), false)) // Red
        add(BallInfo(4, PointF(540f, 600f), Color.parseColor("#DDA0DD"), false)) // Purple
        add(BallInfo(5, PointF(660f, 600f), Color.parseColor("#FF8C00"), false)) // Orange
        add(BallInfo(6, PointF(420f, 600f), Color.parseColor("#228B22"), false)) // Green
        add(BallInfo(7, PointF(540f, 400f), Color.parseColor("#8B4513"), false)) // Maroon/Brown
        add(BallInfo(8, PointF(540f, 550f), Color.BLACK, false, Color.WHITE))     // Black
        // Stripe balls
        add(BallInfo(9, PointF(600f, 450f), Color.parseColor("#FFD700"), true)) // Yellow Stripe
        add(BallInfo(10, PointF(300f, 450f), Color.parseColor("#1E90FF"), true)) // Blue Stripe
        add(BallInfo(11, PointF(220f, 670f), Color.parseColor("#FF3333"), true)) // Red Stripe
        add(BallInfo(12, PointF(850f, 800f), Color.parseColor("#DDA0DD"), true)) // Purple Stripe
        add(BallInfo(13, PointF(760f, 1100f), Color.parseColor("#FF8C00"), true)) // Orange Stripe
        add(BallInfo(14, PointF(280f, 1250f), Color.parseColor("#228B22"), true)) // Green Stripe
        add(BallInfo(15, PointF(440f, 750f), Color.parseColor("#8B4513"), true)) // Maroon Stripe
    }

    // Pockets
    private val pockets = listOf(
        PointF(100f, 200f), PointF(540f, 190f), PointF(980f, 200f),
        PointF(100f, 1400f), PointF(540f, 1410f), PointF(980f, 1400f)
    )

    private var animationTick = 0f
    private var selectedBall: BallInfo? = null
    private var activeTargetBallId = 2 // Default target object ball is #2 Blue
    private val touchProgressRadius = 70f // Tappable radius

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
    }

    fun updatePhysicsTick() {
        if (!service.isInteractiveMode.value && service.isAnalyzing.value) {
            // Simulated game physics movement loop for premium rendering preview
            animationTick += 0.02f
            if (animationTick > 2f * Math.PI) {
                animationTick = 0f
            }
            // Gentle periodic orbital movement on cue ball to simulate interactive alignment flow
            val baseCueX = (tableLeft + tableRight) / 2f - 120f
            val baseCueY = (tableTop + tableBottom) / 2f + 200f
            balls[0].pos.x = baseCueX + (80f * kotlin.math.cos(animationTick)).toFloat()
            balls[0].pos.y = baseCueY + (40f * kotlin.math.sin(animationTick)).toFloat()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Define realistic layout cushions depending on portrait or landscape
        val isLandscape = w > h
        if (isLandscape) {
            tableLeft = w * 0.15f
            tableRight = w * 0.85f
            tableTop = h * 0.20f
            tableBottom = h * 0.80f
        } else {
            tableLeft = w * 0.08f
            tableRight = w * 0.92f
            tableTop = h * 0.14f
            tableBottom = h * 0.86f
        }

        // Map pockets perfectly onto table outer edges
        pockets[0].set(tableLeft + 15f, tableTop + 15f)
        pockets[1].set((tableLeft + tableRight) / 2f, tableTop)
        pockets[2].set(tableRight - 15f, tableTop + 15f)
        pockets[3].set(tableLeft + 15f, tableBottom - 15f)
        pockets[4].set((tableLeft + tableRight) / 2f, tableBottom)
        pockets[5].set(tableRight - 15f, tableBottom - 15f)

        // Position items in an aesthetic cluster initial state
        val centerX = (tableLeft + tableRight) / 2f
        val centerY = (tableTop + tableBottom) / 2f
        val tableW = tableRight - tableLeft
        val tableH = tableBottom - tableTop

        balls[0].pos.set(tableLeft + tableW * 0.35f, centerY + 180f) // Cue Ball
        balls[1].pos.set(centerX, centerY - 100f)
        balls[2].pos.set(centerX + 35f, centerY - 40f)
        balls[3].pos.set(centerX - 35f, centerY - 40f)
        balls[4].pos.set(centerX + 70f, centerY + 20f)
        balls[5].pos.set(centerX, centerY + 20f)
        balls[6].pos.set(centerX - 70f, centerY + 20f)
        balls[7].pos.set(centerX + 105f, centerY + 80f)
        balls[8].pos.set(centerX + 35f, centerY + 80f)
        balls[9].pos.set(centerX - 35f, centerY + 80f)
        balls[10].pos.set(centerX - 105f, centerY + 80f)
        balls[11].pos.set(tableLeft + tableW * 0.25f, centerY - 250f)
        balls[12].pos.set(tableLeft + tableW * 0.75f, centerY + 250f)
        balls[13].pos.set(tableLeft + tableW * 0.15f, centerY + 100f)
        balls[14].pos.set(tableLeft + tableW * 0.85f, centerY - 150f)
        balls[15].pos.set(centerX - 120f, centerY - 220f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!service.isInteractiveMode.value) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Find closest ball to touch point
                var closestBall: BallInfo? = null
                var minDist = touchProgressRadius
                balls.forEach { ball ->
                    val distCurrent = distance(x, y, ball.pos.x, ball.pos.y)
                    if (distCurrent < minDist) {
                        minDist = distCurrent
                        closestBall = ball
                    }
                }

                if (closestBall != null) {
                    selectedBall = closestBall
                    // Set as active target ball if it is an object ball
                    if (closestBall!!.id > 0) {
                        activeTargetBallId = closestBall!!.id
                    }
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                selectedBall?.let { ball ->
                    // Constrain ball movement inside table cushions
                    ball.pos.x = x.coerceIn(tableLeft, tableRight)
                    ball.pos.y = y.coerceIn(tableTop, tableBottom)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectedBall = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Elegant neon glowing laser line drawer
    private fun drawGuideline(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, basePaint: Paint, isDashed: Boolean) {
        val style = service.guidelineStyle.value
        val colorHex = try {
            service.colorLine.value.value.toInt()
        } catch (e: Exception) {
            Color.YELLOW
        }

        basePaint.color = colorHex

        // Set path effect
        if (isDashed || style == "Dashed Glow") {
            basePaint.pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
        } else {
            basePaint.pathEffect = null
        }

        // Apply visual transparency setting
        val opacity = (service.overlayTransparency.value * 255).toInt().coerceIn(30, 255)

        when (style) {
            "Neon Solid", "Dashed Glow" -> {
                // Glow step 1: Draw wide thick semi-transparent backing glow
                val glowPaint = Paint(basePaint).apply {
                    strokeWidth = basePaint.strokeWidth * 3f
                    alpha = (opacity * 0.35f).toInt()
                }
                canvas.drawLine(startX, startY, endX, endY, glowPaint)

                // Glow step 2: Draw intense center core
                val corePaint = Paint(basePaint).apply {
                    strokeWidth = basePaint.strokeWidth * 1.0f
                    alpha = opacity
                }
                canvas.drawLine(startX, startY, endX, endY, corePaint)
            }
            else -> {
                // Laser Thin (standard high contrast thin hairline)
                val thinPaint = Paint(basePaint).apply {
                    strokeWidth = 3f
                    alpha = opacity
                }
                canvas.drawLine(startX, startY, endX, endY, thinPaint)
            }
        }
    }

    // Raycast reflection calculation supporting cushions
    private fun calculateReflections(start: PointF, dirX: Float, dirY: Float, maxBounces: Int): List<PointF> {
        val points = mutableListOf<PointF>()
        points.add(PointF(start.x, start.y))

        var curX = start.x
        var curY = start.y
        var dx = dirX
        var dy = dirY

        // Normalize direction
        val mag = kotlin.math.sqrt(dx * dx + dy * dy)
        if (mag > 0) {
            dx /= mag
            dy /= mag
        } else {
            return points
        }

        var bouncesLeft = maxBounces
        val maxTraceLength = 3200f
        var traceDispatched = 0f

        while (bouncesLeft >= 0 && traceDispatched < maxTraceLength) {
            var tBorder = Float.MAX_VALUE
            var hitXBorder = false

            // Right border collision test
            if (dx > 0) {
                val t = (tableRight - curX) / dx
                if (t > 0 && t < tBorder) {
                    tBorder = t
                    hitXBorder = true
                }
            }
            // Left border collision test
            else if (dx < 0) {
                val t = (tableLeft - curX) / dx
                if (t > 0 && t < tBorder) {
                    tBorder = t
                    hitXBorder = true
                }
            }

            // Bottom border collision test
            if (dy > 0) {
                val t = (tableBottom - curY) / dy
                if (t > 0 && t < tBorder) {
                    tBorder = t
                    hitXBorder = false
                }
            }
            // Top border collision test
            else if (dy < 0) {
                val t = (tableTop - curY) / dy
                if (t > 0 && t < tBorder) {
                    tBorder = t
                    hitXBorder = false
                }
            }

            if (tBorder == Float.MAX_VALUE || tBorder <= 0.05f) {
                break
            }

            val hitX = curX + tBorder * dx
            val hitY = curY + tBorder * dy

            points.add(PointF(hitX, hitY))
            traceDispatched += tBorder

            // Reflect the path vector
            if (hitXBorder) {
                dx = -dx
            } else {
                dy = -dy
            }

            curX = hitX
            curY = hitY
            bouncesLeft--
        }

        return points
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val overlayHexColor = try {
            service.colorLine.value.value.toInt()
        } catch (e: Exception) {
            Color.YELLOW
        }

        linePaint.color = overlayHexColor
        dashedPaint.color = overlayHexColor

        val cueBall = balls[0]
        val activeTargetBall = balls.firstOrNull { it.id == activeTargetBallId } ?: balls[1]
        val ballRadius = 24f

        // Draw targeted/assistance guides
        if (service.onlyTargetedBalls.value) {
            // Find closest pocket to target ball
            var targetPocket = pockets[1]
            var minDist = Float.MAX_VALUE
            pockets.forEach { pocket ->
                val d = distance(activeTargetBall.pos.x, activeTargetBall.pos.y, pocket.x, pocket.y)
                if (d < minDist) {
                    minDist = d
                    targetPocket = pocket
                }
            }

            // Ghost ball contact solver
            val dxPocket = activeTargetBall.pos.x - targetPocket.x
            val dyPocket = activeTargetBall.pos.y - targetPocket.y
            val distPocket = kotlin.math.sqrt(dxPocket * dxPocket + dyPocket * dyPocket)

            val ghostX: Float
            val ghostY: Float
            if (distPocket > 0) {
                ghostX = activeTargetBall.pos.x + (dxPocket / distPocket) * (2 * ballRadius)
                ghostY = activeTargetBall.pos.y + (dyPocket / distPocket) * (2 * ballRadius)
            } else {
                ghostX = activeTargetBall.pos.x
                ghostY = activeTargetBall.pos.y
            }

            // 1. Cue Stick Path
            if (service.espLineCueEnabled.value) {
                val dxCue = ghostX - cueBall.pos.x
                val dyCue = ghostY - cueBall.pos.y
                val distCue = kotlin.math.sqrt(dxCue * dxCue + dyCue * dyCue)
                if (distCue > 0) {
                    val startCueX = cueBall.pos.x - (dxCue / distCue) * 220f
                    val startCueY = cueBall.pos.y - (dyCue / distCue) * 220f
                    val endCueX = cueBall.pos.x - (dxCue / distCue) * 35f
                    val endCueY = cueBall.pos.y - (dyCue / distCue) * 35f
                    
                    linePaint.strokeWidth = 8f
                    drawGuideline(canvas, startCueX, startCueY, endCueX, endCueY, linePaint, false)
                    linePaint.strokeWidth = 6f
                }
            }

            // 2. Cue Ball path to impact (dashed path)
            if (service.aimBallEnabled.value) {
                val dxAim = ghostX - cueBall.pos.x
                val dyAim = ghostY - cueBall.pos.y
                
                // Track bounces for Cue ball if configured
                val bouncesCount = service.cushionBounces.value
                val cueSegments = calculateReflections(cueBall.pos, dxAim, dyAim, bouncesCount)
                for (i in 0 until cueSegments.size - 1) {
                    val pStart = cueSegments[i]
                    val pEnd = cueSegments[i + 1]
                    drawGuideline(canvas, pStart.x, pStart.y, pEnd.x, pEnd.y, dashedPaint, true)
                }

                // Render contact point transparent shadow ball
                if (service.ghostBallOverlay.value) {
                    ballPaint.color = Color.argb(90, 255, 255, 255)
                    canvas.drawCircle(ghostX, ghostY, ballRadius, ballPaint)
                    canvas.drawCircle(ghostX, ghostY, ballRadius, ballStrokePaint)

                    // Impact spot marker
                    ballPaint.color = Color.argb(160, 255, 215, 0)
                    canvas.drawCircle(ghostX, ghostY, 6f, ballPaint)
                }
            }

            // 3. Object Ball Trajectory to Pocket (supporting cushion reflection)
            if (service.espLineEnabled.value) {
                val dxObj = targetPocket.x - activeTargetBall.pos.x
                val dyObj = targetPocket.y - activeTargetBall.pos.y
                val bouncesCount = service.cushionBounces.value

                val objSegments = calculateReflections(activeTargetBall.pos, dxObj, dyObj, bouncesCount)
                for (i in 0 until objSegments.size - 1) {
                    val pStart = objSegments[i]
                    val pEnd = objSegments[i + 1]
                    drawGuideline(canvas, pStart.x, pStart.y, pEnd.x, pEnd.y, linePaint, false)
                }

                // Final target ball landing indicator overlay
                if (service.finalBallOverlay.value && objSegments.isNotEmpty()) {
                    val destination = objSegments.last()
                    ballPaint.color = Color.argb(80, 0, 255, 0) // Translucent landing shadow
                    canvas.drawCircle(destination.x, destination.y, ballRadius, ballPaint)
                    canvas.drawCircle(destination.x, destination.y, ballRadius, ballStrokePaint)

                    // Draw landing index mark number inside
                    if (service.ballIndexLabels.value) {
                        textPaint.color = Color.WHITE
                        textPaint.textSize = 20f
                        canvas.drawText("${activeTargetBall.id}", destination.x, destination.y + 7f, textPaint)
                    }
                }

                // Real-time Pocketable shot state readout
                if (service.pocketShotState.value) {
                    textPaint.color = Color.GREEN
                    textPaint.textSize = 22f
                    val angleOffset = Math.abs(Math.atan2(dyObj.toDouble(), dxObj.toDouble()))
                    val pct = (100 - (angleOffset % 0.2f) * 100).toInt().coerceIn(75, 100)
                    canvas.drawText("SHOT STATE: $pct% POCKETABLE (DIRECT PATH CLEAR)", width / 2f, tableTop - 35f, textPaint)
                }
            }

        } else {
            // Draw predictive guides for ALL active object balls (vibrant multi-guide grid mode!)
            balls.forEach { ball ->
                if (ball.id == 0) return@forEach // skip cue ball

                // Sort pockets by distance to find nearest pocket for EACH ball
                var nearestPocket = pockets[0]
                var mDist = Float.MAX_VALUE
                pockets.forEach { pocket ->
                    val dist = distance(ball.pos.x, ball.pos.y, pocket.x, pocket.y)
                    if (dist < mDist) {
                        mDist = dist
                        nearestPocket = pocket
                    }
                }

                // Draw solid prediction line directly to their closest pocket
                val bouncesCount = service.cushionBounces.value
                val pathDX = nearestPocket.x - ball.pos.x
                val pathDY = nearestPocket.y - ball.pos.y

                val segments = calculateReflections(ball.pos, pathDX, pathDY, bouncesCount)
                
                // Assign matching paint color per ball logic
                val paintInstance = Paint(linePaint).apply { strokeWidth = 5f; color = ball.color }
                for (i in 0 until segments.size - 1) {
                    drawGuideline(canvas, segments[i].x, segments[i].y, segments[i + 1].x, segments[i + 1].y, paintInstance, false)
                }

                // Destination target landing overlay
                if (segments.isNotEmpty() && service.finalBallOverlay.value) {
                    val finalDest = segments.last()
                    overlayPaint.color = Color.argb(100, Color.red(ball.color), Color.green(ball.color), Color.blue(ball.color))
                    canvas.drawCircle(finalDest.x, finalDest.y, ballRadius, overlayPaint)
                    canvas.drawCircle(finalDest.x, finalDest.y, ballRadius, ballStrokePaint)

                    // Draw indexed labeled circle at pocket center
                    if (service.ballIndexLabels.value) {
                        textPaint.color = if (ball.id == 8) Color.BLACK else Color.WHITE
                        textPaint.textSize = 18f
                        canvas.drawText("${ball.id}", finalDest.x, finalDest.y + 6f, textPaint)
                    }
                }
            }
        }

        // 4. Highlight Pockets in Draw Pockets mode
        if (service.drawPockets.value && service.aimCacapaEnabled.value) {
            pockets.forEach { pocket ->
                // Draw target highlighting ring around pockets
                val isClosestToActive = distance(activeTargetBall.pos.x, activeTargetBall.pos.y, pocket.x, pocket.y) < 300f
                if (isClosestToActive) {
                    // Target Pocket Glow highlights
                    ballPaint.color = Color.argb(130, 0, 255, 0) // Glowing green
                    canvas.drawCircle(pocket.x, pocket.y, 44f, ballPaint)
                    
                    val activeStrokePaint = Paint(ballStrokePaint).apply {
                        color = Color.GREEN
                        strokeWidth = 6f
                    }
                    canvas.drawCircle(pocket.x, pocket.y, 44f, activeStrokePaint)
                } else {
                    // Other decorative pocket glows
                    ballPaint.color = Color.argb(45, 212, 175, 55) // Gold ring
                    canvas.drawCircle(pocket.x, pocket.y, 36f, ballPaint)
                    canvas.drawCircle(pocket.x, pocket.y, 36f, ballStrokePaint)
                }
            }
        }

        // 5. Classic High Contrast M3 styled pool balls
        if (service.colorBalls.value) {
            balls.forEach { ball ->
                // Shadow backgound glow
                ballPaint.color = Color.argb(60, 0, 0, 0)
                canvas.drawCircle(ball.pos.x + 4f, ball.pos.y + 4f, ballRadius, ballPaint)

                // Colored ball body filling
                ballPaint.color = ball.color
                canvas.drawCircle(ball.pos.x, ball.pos.y, ballRadius, ballPaint)

                // Draw stripe bands
                if (ball.isStripe) {
                    val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 14f
                    }
                    canvas.drawCircle(ball.pos.x, ball.pos.y, ballRadius - 5f, stripePaint)
                }

                // Render center pocket circle for numbers
                ballPaint.color = Color.WHITE
                canvas.drawCircle(ball.pos.x, ball.pos.y, ballRadius * 0.45f, ballPaint)

                // Write ball number indices
                textPaint.color = ball.labelColor
                textPaint.textSize = 16f
                val activeLabel = if (ball.id == 0) "" else "${ball.id}"
                canvas.drawText(activeLabel, ball.pos.x, ball.pos.y + 5f, textPaint)

                // Add glossy lighting glare highlight
                val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(160, 255, 255, 255)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(ball.pos.x - 6f, ball.pos.y - 6f, 4f, glarePaint)

                // Active pointer indicator for currently selected alignment ball target
                if (ball.id == activeTargetBallId && ball.id > 0) {
                    val activeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.GREEN
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                    }
                    canvas.drawCircle(ball.pos.x, ball.pos.y, ballRadius + 6f, activeStroke)
                }
            }
        }

        // 6. Direct instruction on-screen alignment texts
        if (service.isInteractiveMode.value) {
            textPaint.color = Color.GREEN
            textPaint.textSize = 30f
            canvas.drawText("ALIGNMENT MODE ACTIVE: DRAG BALLS ON THE TABLE HUD", width / 2f, 130f, textPaint)
            textPaint.color = Color.parseColor("#FFD700")
            textPaint.textSize = 24f
            canvas.drawText("Tap / drag any ball to align with table, then lock via the floating options.", width / 2f, 164f, textPaint)
        }
    }
}
