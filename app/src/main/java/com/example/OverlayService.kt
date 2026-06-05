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
                            isAnalyzing = isAnalyzing
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
    isAnalyzing: MutableState<Boolean>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GlassBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(260.dp)
            .border(1.dp, BloodRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isAnalyzing.value) Color.Green else Color.Red)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ITS ENGINE v3.6",
                        color = GoldYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Row {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Minimize",
                            tint = White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop",
                            tint = BloodRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Divider(color = BloodRed.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

            // Configuration Options
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EngineToggleRow(label = "AIM CUE BALL", checked = aimBall.value, onCheckedChange = { aimBall.value = it })
                EngineToggleRow(label = "AIM POCKET assistance", checked = aimCacapa.value, onCheckedChange = { aimCacapa.value = it })
                EngineToggleRow(label = "ESP TRAJECTORY line", checked = espLine.value, onCheckedChange = { espLine.value = it })
                EngineToggleRow(label = "ESP BALL TO BALL", checked = espLineBall.value, onCheckedChange = { espLineBall.value = it })
                EngineToggleRow(label = "ESP CUE ALIGNMENT", checked = espLineCue.value, onCheckedChange = { espLineCue.value = it })
                EngineToggleRow(label = "COLOR BALL highlights", checked = colorBalls.value, onCheckedChange = { colorBalls.value = it })
                EngineToggleRow(label = "REAL-TIME FRAME CV", checked = isAnalyzing.value, onCheckedChange = { isAnalyzing.value = it })
            }

            Divider(color = BloodRed.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Color Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "LINE COLOR", color = LightGrey, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val colors = listOf(Color(0xFFFFD700) to "Gold", Color(0xFF8B0000) to "Red", Color(0xFF00FF00) to "Green", Color(0xFF00FFFF) to "Cyan")
                    colors.forEach { (colorObj, name) ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colorObj)
                                .border(
                                    width = if (lineColor.value == colorObj) 2.dp else 0.dp,
                                    color = White,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    lineColor.value = colorObj
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Mode: OpenCV + OpenGL ES Layer Mode",
                color = LightGrey.copy(alpha = 0.8f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
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
        Text(text = label, color = White, fontSize = 11.sp, fontWeight = FontWeight.Normal)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoldYellow,
                checkedTrackColor = BloodRed,
                uncheckedThumbColor = LightGrey,
                uncheckedTrackColor = DeepBlack
            ),
            modifier = Modifier.height(20.dp)
        )
    }
}
