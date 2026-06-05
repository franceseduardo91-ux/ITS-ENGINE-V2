package com.example

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.BloodRed
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.AmbientGrey
import com.example.ui.theme.LightGrey
import com.example.ui.theme.White

class MainActivity : ComponentActivity() {

    private val OVERLAY_PERMISSION_REQUEST_CODE = 5469
    private var isKeyValidated = mutableStateOf(false)

    // Media projection launcher
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startOverlayEngineService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Media Projection captures are required for frame CV analysis.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DeepBlack
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        BackgroundGeometricGlow()

                        if (!isKeyValidated.value) {
                            LoginScreen(
                                onKeyValidated = {
                                    isKeyValidated.value = true
                                }
                            )
                        } else {
                            HomeScreen(
                                onStartPressed = {
                                    checkAndRequestOverlayPermission()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Alert Permission Required")
                .setMessage("ITS ENGINE needs Draw Over Other Apps overlay access to display physics trajectories on your screen during frame analysis.")
                .setPositiveButton("GRANT") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("CANCEL", null)
                .show()
        } else {
            requestMediaProjectionCapture()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                requestMediaProjectionCapture()
            } else {
                Toast.makeText(this, "Draw Overlay authorization was declined.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestMediaProjectionCapture() {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        projectionLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    private fun startOverlayEngineService(resultCode: Int, resultData: Intent) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("RESULT_CODE", resultCode)
            putExtra("RESULT_DATA", resultData)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Exit activity after launching overlay engine to let users enjoy HUD overlay assistance immediately
        Toast.makeText(this, "ITS ENGINE overlay loaded successfully!", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@Composable
fun BackgroundGeometricGlow() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BloodRed.copy(alpha = 0.25f), DeepBlack),
                    radius = 1200f
                )
            )
    )
}

@Composable
fun LoginScreen(onKeyValidated: () -> Unit) {
    var accessKey by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(BloodRed, DeepBlack)))
                .border(2.dp, GoldYellow, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ITS",
                color = GoldYellow,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ITS ENGINE PASSWORD",
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "ACCESS DECRYPTOR SYSTEM",
            color = GoldYellow,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = AmbientGrey),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BloodRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = accessKey,
                    onValueChange = { 
                        accessKey = it
                        errorMessage = "" 
                    },
                    label = { Text("Cloud Access Key", color = LightGrey) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { 
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = BloodRed) 
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = GoldYellow,
                        unfocusedBorderColor = BloodRed,
                        cursorColor = GoldYellow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (accessKey == "ITSENGINE2026CLOUD") {
                            onKeyValidated()
                        } else {
                            errorMessage = "CRITICAL ERROR: INVALID ENGINE CLOUD ACCESS KEY"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, GoldYellow, RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "VALIDATE ENCRYPTION",
                        color = White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onStartPressed: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        // Large Premium Dashboard Logo
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(BloodRed, DeepBlack)
                        )
                    )
                    .border(3.dp, GoldYellow, RoundedCornerShape(60.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ITS",
                    color = GoldYellow,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ITS ENGINE",
                color = White,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            Text(
                text = "COMPUTER VISION PHYSICS AR HUD",
                color = GoldYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Feature cards
        Card(
            colors = CardDefaults.cardColors(containerColor = AmbientGrey),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BloodRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeaturesInfoRow("Real-Time Frame Capture", "Pipes system MediaProjection frames directly to physics buffers.")
                FeaturesInfoRow("OpenCV Contour Engine", "Auto detect table rail contours, ball colors, and pockets.")
                FeaturesInfoRow("Trajectory Rebound Simulator", "Physically models cushions bounce vectors & angles.")
                FeaturesInfoRow("AR HUD Floating Overlay", "Shows exact billiard direction indicators using SurfaceViews.")
            }
        }

        // START BUTTON
        Button(
            onClick = onStartPressed,
            colors = ButtonDefaults.buttonColors(containerColor = BloodRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .border(2.dp, GoldYellow, RoundedCornerShape(12.dp))
        ) {
            Text(
                text = "START ENGINE HUD",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
fun FeaturesInfoRow(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(GoldYellow)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = desc, color = LightGrey, fontSize = 11.sp)
        }
    }
}
