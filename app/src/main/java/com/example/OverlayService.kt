package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.BloodRed
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.AmbientGrey
import com.example.ui.theme.LightGrey
import com.example.ui.theme.White
import com.example.ui.theme.GlassBg
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var canvasView: OverlayCanvasView? = null
    private var composeLifecycleOwner: ComposeLifecycleOwner? = null
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val handler = Handler(Looper.getMainLooper())

    // Simulated real-time CV engine and Physics pipeline state fields
    var aimBallEnabled = mutableStateOf(true)
    var aimCacapaEnabled = mutableStateOf(true)
    var espLineEnabled = mutableStateOf(true)
    var espLineBallEnabled = mutableStateOf(true)
    var espLineCueEnabled = mutableStateOf(true)
    var colorLine = mutableStateOf(Color(0xFFFFD700)) // Gold by default
    var colorBalls = mutableStateOf(true)
    var isAnalyzing = mutableStateOf(true)
    var isInteractiveMode = mutableStateOf(false)

    // Easy Victory premium local features mapped
    var onlyTargetedBalls = mutableStateOf(false)
    var drawPockets = mutableStateOf(true)
    var pocketShotState = mutableStateOf(true)
    var ghostBallOverlay = mutableStateOf(true)
    var finalBallOverlay = mutableStateOf(true)
    var ballIndexLabels = mutableStateOf(true)
    var cushionBounces = mutableStateOf(1) // 1-3 cushions
    var guidelineStyle = mutableStateOf("Neon Solid")
    var overlayTransparency = mutableStateOf(1.0f)

    fun setCanvasTouchable(touchable: Boolean) {
        val view = canvasView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        if (touchable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        windowManager.updateViewLayout(view, params)
    }

    // JNI Native Bridge Simulation values
    init {
        // Here we would load JNI:
        // System.loadLibrary("ItsEngine")
        // System.loadLibrary("Fisica")
        // System.loadLibrary("Aimpool")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", -1) ?: -1
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")

        startForegroundServiceWithNotification()

        if (resultCode != -1 && resultData != null) {
            setupMediaProjection(resultCode, resultData)
        }

        showFloatingPanel()
        showAROverlayCanvas()

        // Start processing background tick
        startProcessingLoop()

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "ITS_ENGINE_SERVICE"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ITS ENGINE Active")
            .setContentText("Computer vision and physical trajectory overlay is running.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1001, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ITS_ENGINE_SERVICE",
                "ITS Engine Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun setupMediaProjection(resultCode: Int, resultData: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

        // Setup VirtualDisplay to capture screen frames for simulated OpenCV pipeline
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ITSEngineCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null, // Surface to pipe frames can go here (e.g. ImageReader or MediaCodec)
            null,
            null
        )
    }

    private fun showFloatingPanel() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        val composeView = ComposeView(this).apply {
            // Setup proper lifecycles for Jetpack Compose inside Android Service Overlay
            val lifecycleOwner = ComposeLifecycleOwner().also {
                composeLifecycleOwner = it
                it.start()
            }
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                var isExpanded by remember { mutableStateOf(true) }
                var offsetX by remember { mutableStateOf(100f) }
                var offsetY by remember { mutableStateOf(200f) }

                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                                params.x = offsetX.roundToInt()
                                params.y = offsetY.roundToInt()
                                windowManager.updateViewLayout(overlayView, params)
                            }
                        }
                ) {
                    if (isExpanded) {
                        FloatingDashboard(
                            onCollapse = { isExpanded = false },
                            onClose = { stopSelf() },
                            aimBall = aimBallEnabled,
                            aimCacapa = aimCacapaEnabled,
                            espLine = espLineEnabled,
                            espLineBall = espLineBallEnabled,
                            espLineCue = espLineCueEnabled,
                            lineColor = colorLine,
                            colorBalls = colorBalls,
                            isAnalyzing = isAnalyzing,
                            isInteractiveMode = isInteractiveMode,
                            onlyTargetedBalls = onlyTargetedBalls,
                            drawPockets = drawPockets,
                            pocketShotState = pocketShotState,
                            ghostBallOverlay = ghostBallOverlay,
                            finalBallOverlay = finalBallOverlay,
                            ballIndexLabels = ballIndexLabels,
                            cushionBounces = cushionBounces,
                            guidelineStyle = guidelineStyle,
                            overlayTransparency = overlayTransparency,
                            onInteractiveModeChanged = { enabled ->
                                setCanvasTouchable(enabled)
                            }
                        )
                    } else {
                        FloatingMiniButton(
                            onExpand = { isExpanded = true }
                        )
                    }
                }
            }
        }

        overlayView = composeView
        windowManager.addView(overlayView, params)
    }

    private fun showAROverlayCanvas() {
        if (canvasView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        canvasView = OverlayCanvasView(this, this)
        windowManager.addView(canvasView, params)
    }

    private fun startProcessingLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (isAnalyzing.value) {
                    // Update physics trajectory predictions periodically
                    canvasView?.updatePhysicsTick()
                }
                handler.postDelayed(this, 16) // ~60fps processing and rendering updates
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
        canvasView?.let { windowManager.removeView(it) }
        virtualDisplay?.release()
        mediaProjection?.stop()
        handler.removeCallbacksAndMessages(null)
        composeLifecycleOwner?.destroy()
    }
}

// Robust unified lifecycle owner for Jetpack Compose inside android Service overlay
class ComposeLifecycleOwner : androidx.lifecycle.LifecycleOwner, androidx.lifecycle.ViewModelStoreOwner, androidx.savedstate.SavedStateRegistryOwner {
    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this)
    private val store = androidx.lifecycle.ViewModelStore()
    private val controller = androidx.savedstate.SavedStateRegistryController.create(this)

    init {
        controller.performAttach()
        controller.performRestore(null)
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.CREATED
    }

    override val lifecycle: androidx.lifecycle.Lifecycle get() = lifecycleRegistry
    override val viewModelStore: androidx.lifecycle.ViewModelStore get() = store
    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.STARTED
    }

    fun destroy() {
        lifecycleRegistry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
        store.clear()
    }
}

@Composable
fun FloatingMiniButton(onExpand: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(GlassBg)
            .border(2.dp, BloodRed, RoundedCornerShape(27.dp))
            .clickable { onExpand() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(GoldYellow)
        )
    }
}

@Composable
fun FloatingDashboard(
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    aimBall: MutableState<Boolean>,
    aimCacapa: MutableState<Boolean>,
    espLine: MutableState<Boolean>,
    espLineBall: MutableState<Boolean>,
    espLineCue: MutableState<Boolean>,
    lineColor: MutableState<Color>,
    colorBalls: MutableState<Boolean>,
    isAnalyzing: MutableState<Boolean>,
    isInteractiveMode: MutableState<Boolean>,
    onlyTargetedBalls: MutableState<Boolean>,
    drawPockets: MutableState<Boolean>,
    pocketShotState: MutableState<Boolean>,
    ghostBallOverlay: MutableState<Boolean>,
    finalBallOverlay: MutableState<Boolean>,
    ballIndexLabels: MutableState<Boolean>,
    cushionBounces: MutableState<Int>,
    guidelineStyle: MutableState<String>,
    overlayTransparency: MutableState<Float>,
    onInteractiveModeChanged: (Boolean) -> Unit
) {
    var activeTab by remember { mutableStateOf("VISUAL") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xEB0F0F14)), // Premium dark glass
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(280.dp)
            .border(1.2.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(12.dp)) // Royal gold border accent
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Header Info Bar (EV PRO Header Style)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Green)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EV.DEVIP | ITS ENGINE",
                        color = Color(0xFFD4AF37), // Metallic gold
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Minimize",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop",
                            tint = Color(0xFFFF4D4D),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PRO | LOCAL OFFLINE ACTIVE",
                color = Color.LightGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation Tabs Bar (AIM | VISUAL | MISC | PROFILE)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E26), RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val tabs = listOf("AIM", "VISUAL", "MISC", "PROFILE")
                tabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) Color(0xFF4C1010) else Color.Transparent) // Dark amber/red highlight
                            .clickable { activeTab = tab }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color(0xFFD4AF37) else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab contents
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (activeTab) {
                    "AIM" -> {
                        // Cushion Bounces Selector Selector
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "CUSHION BOUNCES",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (0..3).forEach { num ->
                                    val isSel = cushionBounces.value == num
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSel) Color(0xFFD4AF37) else Color(0xFF252530))
                                            .clickable { cushionBounces.value = num }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (num == 0) "Direct" else "$num Cushion",
                                            color = if (isSel) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        EngineToggleRow(label = "AIM CUE BALL", checked = aimBall.value, onCheckedChange = { aimBall.value = it })
                        EngineToggleRow(label = "ESP CUE ALIGNMENT", checked = espLineCue.value, onCheckedChange = { espLineCue.value = it })
                        EngineToggleRow(label = "ESP BALL TO BALL", checked = espLineBall.value, onCheckedChange = { espLineBall.value = it })
                        EngineToggleRow(label = "AIM POCKET GLOWS", checked = aimCacapa.value, onCheckedChange = { aimCacapa.value = it })
                        EngineToggleRow(label = "SHOW STICK PATH", checked = espLine.value, onCheckedChange = { espLine.value = it })
                    }

                    "VISUAL" -> {
                        EngineToggleRow(label = "Only Targeted Balls", checked = onlyTargetedBalls.value, onCheckedChange = { onlyTargetedBalls.value = it })
                        EngineToggleRow(label = "Draw Pockets", checked = drawPockets.value, onCheckedChange = { drawPockets.value = it })
                        EngineToggleRow(label = "Pocket Shot State", checked = pocketShotState.value, onCheckedChange = { pocketShotState.value = it })
                        EngineToggleRow(label = "Ghost Ball Overlay", checked = ghostBallOverlay.value, onCheckedChange = { ghostBallOverlay.value = it })
                        EngineToggleRow(label = "Final Ball Overlay", checked = finalBallOverlay.value, onCheckedChange = { finalBallOverlay.value = it })
                        EngineToggleRow(label = "Ball Index Labels", checked = ballIndexLabels.value, onCheckedChange = { ballIndexLabels.value = it })
                        EngineToggleRow(label = "High Contrast Balls", checked = colorBalls.value, onCheckedChange = { colorBalls.value = it })
                    }

                    "MISC" -> {
                        EngineToggleRow(
                            label = "ALIGN MODE (Draggable)",
                            checked = isInteractiveMode.value,
                            onCheckedChange = {
                                isInteractiveMode.value = it
                                onInteractiveModeChanged(it)
                            }
                        )

                        // Line color switcher
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "LINE COLOR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val colors = listOf(
                                    Color(0xFFFFD43F) to "Gold",
                                    Color(0xFFFF4D4D) to "Red",
                                    Color(0xFF4DFF4D) to "Green",
                                    Color(0xFF4DFFFF) to "Cyan",
                                    Color(0xFFE04DFF) to "Pink"
                                )
                                colors.forEach { (colorObj, _) ->
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colorObj)
                                            .border(
                                                width = if (lineColor.value == colorObj) 2.dp else 0.dp,
                                                color = Color.White,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable { lineColor.value = colorObj }
                                    )
                                }
                            }
                        }

                        // Trajectory style options
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "GUIDELINE VISUAL STYLE",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val styles = listOf("Neon Solid", "Dashed Glow", "Laser Thin")
                                styles.forEach { style ->
                                    val isSel = guidelineStyle.value == style
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSel) Color(0xFFD4AF37) else Color(0xFF252530))
                                            .clickable { guidelineStyle.value = style }
                                            .padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = style,
                                            color = if (isSel) Color.Black else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Transparency slider
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "HUD OPACITY", color = Color.White, fontSize = 10.sp)
                                Text(text = "${(overlayTransparency.value * 100).roundToInt()}%", color = Color(0xFFD4AF37), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = overlayTransparency.value,
                                onValueChange = { overlayTransparency.value = it },
                                valueRange = 0.3f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFD4AF37),
                                    activeTrackColor = Color(0xFFD4AF37),
                                    inactiveTrackColor = Color.Gray
                                ),
                                modifier = Modifier.height(20.dp)
                            )
                        }

                        EngineToggleRow(label = "CPU CORE ENGINE", checked = isAnalyzing.value, onCheckedChange = { isAnalyzing.value = it })
                    }

                    "PROFILE" -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ProfileTextRow("STATUS", "VIP COLD-LICENSED", Color.Green)
                                ProfileTextRow("SERVER CAPTURE", "LOCAL DECRYPTED (OFFLINE)", Color(0xFFD4AF37))
                                ProfileTextRow("NATIVE SIMULATION", "ACTIVE 16-BALLS", Color.White)
                                ProfileTextRow("PHYSICS ENGINE", "ITS NATIVE REFLECTION-CV", Color.White)
                                ProfileTextRow("DEVICE COMPAT", "ADAPTIVE WINDOW SCALING", Color.Cyan)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ITS ENGINE PRO - OFFLINE DIRECT SIMULATOR",
                color = Color.Gray,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun ProfileTextRow(label: String, value: String, valColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = valColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun EngineToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Normal)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFD4AF37),
                checkedTrackColor = Color(0xFF4C1010),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E1E26)
            ),
            modifier = Modifier.height(20.dp)
        )
    }
}
