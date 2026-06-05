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
import android.media.Image
import android.media.ImageReader
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
import androidx.compose.ui.text.font.FontFamily
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

    // ITS ENGINE V1 Core Custom States
    var isOpenCvEnabled = mutableStateOf(false)
    var isLineEnabled = mutableStateOf(true)
    var isAiLineEnabled = mutableStateOf(false)
    var detectedCueX = mutableStateOf(-1f)
    var detectedCueY = mutableStateOf(-1f)

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

    private var imageReader: ImageReader? = null
    private var lastScannedTime = 0L

    private fun setupMediaProjection(resultCode: Int, resultData: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        // Downscale capture frame sizes to prevent image-buffer allocation delays
        val captureWidth = screenWidth / 4
        val captureHeight = screenHeight / 4

        try {
            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2).apply {
                setOnImageAvailableListener({ reader ->
                    if (isOpenCvEnabled.value || isAiLineEnabled.value) {
                        processScreenFrame(reader)
                    }
                }, handler)
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ITSEngineCapture",
                captureWidth,
                captureHeight,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processScreenFrame(reader: ImageReader) {
        val currentTime = System.currentTimeMillis()
        // limit scan to 10fps to avoid performance signature or battery drain
        if (currentTime - lastScannedTime < 100) return
        lastScannedTime = currentTime

        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return
            val planes = image.planes
            if (planes.isEmpty()) return
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height

            var whiteCount = 0
            var sumX = 0f
            var sumY = 0f

            // Scan pixels in a fast grid
            val yStep = 6
            val xStep = 6
            for (y in 0 until height step yStep) {
                for (x in 0 until width step xStep) {
                    val offset = y * rowStride + x * pixelStride
                    if (offset + 2 < buffer.remaining()) {
                        val r = buffer.get(offset).toInt() and 0xFF
                        val g = buffer.get(offset + 1).toInt() and 0xFF
                        val b = buffer.get(offset + 2).toInt() and 0xFF

                        // A high-brightness white or very light pixel characteristic of the pool white guides/balls
                        if (r > 240 && g > 240 && b > 240) {
                            whiteCount++
                            sumX += x
                            sumY += y
                        }
                    }
                }
            }

            if (whiteCount > 10) {
                val foundX = (sumX / whiteCount) * 4f // map back to full size
                val foundY = (sumY / whiteCount) * 4f
                
                // Filter out boundary outliers
                if (foundX > 100 && foundY > 100) {
                    handler.post {
                        detectedCueX.value = foundX
                        detectedCueY.value = foundY
                    }
                }
            }
        } catch (e: Exception) {
            // fail-silent
        } finally {
            image?.close()
        }
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
                            detectDragGestures(
                                onDragEnd = {
                                    val metrics = DisplayMetrics()
                                    windowManager.defaultDisplay.getRealMetrics(metrics)
                                    val screenWidth = metrics.widthPixels
                                    // Snaps to screen edges (gruda no canto da tela)
                                    if (offsetX < screenWidth / 2f) {
                                        offsetX = 10f
                                    } else {
                                        offsetX = (screenWidth - if (isExpanded) 440f else 180f)
                                    }
                                    params.x = offsetX.roundToInt()
                                    windowManager.updateViewLayout(overlayView, params)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                    params.x = offsetX.roundToInt()
                                    params.y = offsetY.roundToInt()
                                    windowManager.updateViewLayout(overlayView, params)
                                }
                            )
                        }
                ) {
                    if (isExpanded) {
                        FloatingDashboard(
                            onCollapse = { isExpanded = false },
                            onClose = { stopSelf() },
                            isOpenCv = isOpenCvEnabled,
                            isLine = isLineEnabled,
                            isAiLine = isAiLineEnabled,
                            isInteractiveMode = isInteractiveMode,
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
        imageReader?.close()
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
            .size(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF8B1010)) 
            .border(2.5.dp, Color(0xFFD4AF37), RoundedCornerShape(28.dp))
            .clickable { onExpand() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "ITS",
            color = Color(0xFFD4AF37),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun FloatingDashboard(
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    isOpenCv: MutableState<Boolean>,
    isLine: MutableState<Boolean>,
    isAiLine: MutableState<Boolean>,
    isInteractiveMode: MutableState<Boolean>,
    onInteractiveModeChanged: (Boolean) -> Unit
) {
    var activeTab by remember { mutableStateOf("ABA 1") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xF20B0B0F)), // Liquid dark premium dashboard
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .size(430.dp) // Requested EXACT 430x430 physical card size
            .border(2.dp, Color(0xFFD4AF37).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Info Bar (ITS Engine Pro)
            Column {
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
                                .background(Color.Green)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ITS ENGINE V1 PRO",
                            color = Color(0xFFD4AF37),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onCollapse,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Minimize",
                                tint = Color.White,
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
                                tint = Color(0xFFFF4D4D),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "STATUS: ONLINE ACTIVE",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "VIP OFFRESH-CV v1",
                        color = Color(0xFFD4AF37),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Robust Exactly 2 Abas/Tabs selection
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF16161F), RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("ABA 1", "ABA 2").forEach { tab ->
                        val isSelected = activeTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF5C1212) else Color.Transparent)
                                .clickable { activeTab = tab }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected) Color(0xFFD4AF37) else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // Central scrollable/scanned area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeTab == "ABA 1") {
                        Text(
                            text = "OPÇÕES DE PROCESSAMENTO GRÁFICO",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        EngineToggleRow(
                            label = "Ativar OpenCV",
                            checked = isOpenCv.value,
                            onCheckedChange = { isOpenCv.value = it }
                        )
                        Text(
                            text = "Faz varredura e detecta a linha branca do taco do 8 Ball Pool automaticamente por cores.",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                        )

                        EngineToggleRow(
                            label = "Ativar Line",
                            checked = isLine.value,
                            onCheckedChange = { isLine.value = it }
                        )
                        Text(
                            text = "Desenha a trajetória física das bolas prevendo ricochetes.",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                        )
                    } else {
                        // ABA 2 Options
                        Text(
                            text = "INTELIGÊNCIA ARTIFICIAL ATIVA",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        EngineToggleRow(
                            label = "Aí Line Automatica",
                            checked = isAiLine.value,
                            onCheckedChange = { isAiLine.value = it }
                        )
                        Text(
                            text = "Detecção em tempo real de tacada e cálculo rápido de trajetórias em formato automático autônomo.",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                        )

                        // Styling additions mapped cleanly inside ABA 2 for added values
                        Divider(color = Color.Gray.copy(alpha = 0.15f), thickness = 1.dp)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CALIBRATION OVERLAYS",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Button(
                                onClick = { 
                                    isInteractiveMode.value = !isInteractiveMode.value
                                    onInteractiveModeChanged(isInteractiveMode.value)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isInteractiveMode.value) Color(0xFF5C1212) else Color(0xFF1E1E2C)),
                                modifier = Modifier.height(26.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isInteractiveMode.value) "Lock Table" else "Align Table",
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Bottom credits / info bar
            Column {
                Divider(color = Color.Gray.copy(alpha = 0.2f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ITS ENGINE V1 © 2026 NATIVE PHYSICS AR HUD",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
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
