package com.example.ui

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.*
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import com.example.network.ControlCommand
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val clientFacing by viewModel.clientFacing.collectAsStateWithLifecycle()
    val clientResolution by viewModel.clientResolution.collectAsStateWithLifecycle()
    val clientFps by viewModel.clientFps.collectAsStateWithLifecycle()
    val flashOn by viewModel.flashOn.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val isRecordingLocally by viewModel.isRecordingLocally.collectAsStateWithLifecycle()
    val clientIp by viewModel.clientIp.collectAsStateWithLifecycle()
    val isConnected by viewModel.isClientConnectedToHost.collectAsStateWithLifecycle()
    val batterySaver by viewModel.batterySaver.collectAsStateWithLifecycle()
    val silentMode by viewModel.silentMode.collectAsStateWithLifecycle()

    var hostIpInput by remember { mutableStateOf("192.168.43.1") } // Common Android hotspot gateway
    var showQrPairingDialog by remember { mutableStateOf(false) }
    var isScanningQr by remember { mutableStateOf(false) }

    // Re-bind camera whenever settings are updated
    LaunchedEffect(clientFacing, clientResolution, clientFps) {
        // Safe check for previewView
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Scaffold(
        topBar = {
            if (!batterySaver) {
                Column(
                    modifier = Modifier
                        .background(BlackBackground)
                        .statusBarsPadding()
                ) {
                    // Dynamic Real Status Bar
                    RealStatusBar(pulseAlpha)

                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    "CLIENT CAMERA",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    "IP Address: $clientIp",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            if (!isConnected) {
                                IconButton(onClick = { isScanningQr = true }) {
                                    Icon(Icons.Filled.QrCode, contentDescription = "Scan Host QR", tint = Color.White)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBackground)
                    )
                }
            }
        },
        containerColor = BlackBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (batterySaver) PaddingValues(0.dp) else paddingValues)
        ) {
            if (isConnected) {
                // Camera Viewfinder (Only active after connected!)
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        viewModel.cameraManager.bindCamera(
                            lifecycleOwner = lifecycleOwner,
                            surfaceProvider = previewView.surfaceProvider,
                            facing = clientFacing,
                            resolution = clientResolution,
                            fps = clientFps,
                            onFrameCaptured = { jpegBytes ->
                                viewModel.sendLocalFrame(jpegBytes)
                            },
                            onQrCodeScanned = null
                        )
                    }
                )
            } else {
                // Standby Offline view
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BlackBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(GrayCard)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VideocamOff,
                                contentDescription = "Camera Offline",
                                tint = RedPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "CAMERA STANDBY",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Connect to a Host console to activate camera streaming.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                    }
                }
            }

            // Viewfinder Dimmed Overlay (for aesthetic focus)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
            )

            // Active Scan Overlay
            if (isScanningQr && !isConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { previewView ->
                            viewModel.cameraManager.bindCamera(
                                lifecycleOwner = lifecycleOwner,
                                surfaceProvider = previewView.surfaceProvider,
                                facing = "REAR",
                                resolution = "720p",
                                fps = 30,
                                onFrameCaptured = {},
                                onQrCodeScanned = { qrText ->
                                    if (qrText.startsWith("live_camera_host:")) {
                                        val scannedHostIp = qrText.substringAfter("live_camera_host:")
                                        if (scannedHostIp.isNotEmpty()) {
                                            viewModel.connectClientToHost(scannedHostIp)
                                            isScanningQr = false
                                        }
                                    } else if (qrText.isNotEmpty() && qrText.matches(Regex("""^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$"""))) {
                                        viewModel.connectClientToHost(qrText)
                                        isScanningQr = false
                                    }
                                }
                            )
                        }
                    )

                    // Scanner overlay with cutout
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(
                                text = "SCAN HOST QR CODE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Align the Host's pairing QR code inside the box to connect",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .border(2.dp, RedPrimary, RoundedCornerShape(16.dp))
                                .background(Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            val laserTransition = rememberInfiniteTransition(label = "laser")
                            val laserYOffset by laserTransition.animateFloat(
                                initialValue = -120f,
                                targetValue = 120f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = LinearOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "laser_y"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .offset(y = laserYOffset.dp)
                                    .background(RedPrimary)
                            )
                        }

                        Button(
                            onClick = { isScanningQr = false },
                            colors = ButtonDefaults.buttonColors(containerColor = GrayCard),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            Text("CANCEL SCANNING", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Top Status Panel overlays (if not saver mode)
            if (!batterySaver) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Connection Status Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isConnected) GreenActive.copy(alpha = 0.15f) else RedPrimary.copy(alpha = 0.15f))
                            .border(
                                1.dp,
                                if (isConnected) GreenActive else RedPrimary,
                                RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .align(Alignment.Start)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isConnected) GreenActive else RedPrimary, CircleShape)
                            )
                            Text(
                                text = if (isConnected) "CONNECTED TO HOST" else "DISCONNECTED",
                                color = if (isConnected) GreenActive else RedPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Recording Pill
                    if (isRecordingLocally) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(RedPrimary.copy(alpha = 0.15f))
                                .border(1.dp, RedPrimary, RoundedCornerShape(24.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .align(Alignment.Start)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(RedPrimary, CircleShape)
                                )
                                Text(
                                    text = "LOCAL RECORDING ACTIVE",
                                    color = RedPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Console Panels (if not connected, show pairing card)
            if (!batterySaver) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Floating Local Viewfinder Actions (Switch, Flash, Record)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flash
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { viewModel.toggleLocalFlash() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = "Flash Toggle",
                                tint = if (flashOn) RedPrimary else Color.White
                            )
                        }

                        // Local Record Trigger Button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(2.dp, Color.White, CircleShape)
                                .clickable {
                                    if (isRecordingLocally) {
                                        viewModel.stopLocalRecording()
                                    } else {
                                        viewModel.startLocalRecording()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isRecordingLocally) 24.dp else 48.dp)
                                    .clip(if (isRecordingLocally) RoundedCornerShape(4.dp) else CircleShape)
                                    .background(RedPrimary)
                            )
                        }

                        // Switch Camera facing
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { viewModel.toggleLocalCameraFacing() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Cameraswitch,
                                contentDescription = "Switch Camera",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // CRITICAL: OLED BATTERY SAVER DIMMER MODE OVERLAY
            if (batterySaver) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable {
                            // Let client touch screen to exit dimmer locally!
                            viewModel.triggerRemoteCommand("", ControlCommand("SET_BATTERY_SAVER", boolValue = false))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.BatteryChargingFull,
                            contentDescription = "Battery saving mode active",
                            tint = GreenActive.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            "OLED BATTERY SAVER SHIELD ACTIVE",
                            color = Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Live streaming continues smoothly in the background. Tap the screen to wake up the viewfinder.",
                            color = TextSecondary.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(GreenActive.copy(alpha = 0.4f), CircleShape)
                            )
                            Text(
                                "STREAMING LIVE • ${clientResolution.uppercase()}",
                                color = GreenActive.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // QR Code Pairing Overlay Dialog
        if (showQrPairingDialog) {
            AlertDialog(
                onDismissRequest = { showQrPairingDialog = false },
                title = {
                    Text(
                        "PAIRING CAMERA CLIENT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Scan this QR code with the Host console or type the coordinates below manually.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        // QR Canvas generator
                        val pairingText = "live_camera:$clientIp:9000"
                        val qrBitmap = remember(pairingText) {
                            QrCodeUtils.generateQrCode(pairingText, 300)
                        }

                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Pairing QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Text(
                            "IP Address: $clientIp\nPort: 9000",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQrPairingDialog = false }) {
                        Text("CLOSE", color = RedPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = GraySurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
