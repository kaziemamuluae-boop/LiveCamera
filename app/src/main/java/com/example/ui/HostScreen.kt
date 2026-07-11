package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ConnectedClientInfo
import com.example.network.ControlCommand
import com.example.ui.theme.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val clients by viewModel.connectedClients.collectAsStateWithLifecycle()
    val clientFrames by viewModel.clientFramesState.collectAsStateWithLifecycle()

    var selectedGridSize by remember { mutableStateOf(4) }
    var selectedClientForControl by remember { mutableStateOf<ConnectedClientInfo?>(null) }
    var fullscreenClient by remember { mutableStateOf<ConnectedClientInfo?>(null) }
    var isRecordingAll by remember { mutableStateOf(false) }
    var showHostQrDialog by remember { mutableStateOf(false) }
    var showGridDropdown by remember { mutableStateOf(false) }

    // Automatically close QR pairing dialog when a client connects
    LaunchedEffect(clients.size) {
        if (clients.isNotEmpty()) {
            showHostQrDialog = false
        }
    }

    // Keep screen on while there are connected clients
    val view = LocalView.current
    DisposableEffect(clients.isNotEmpty()) {
        if (clients.isNotEmpty()) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Derive grid columns based on layout selection
    val columns = when (selectedGridSize) {
        4 -> 2
        6 -> 2
        9 -> 3
        12 -> 3
        16 -> 4
        else -> 2
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
                                "HOST CONSOLE",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                "Connected Devices: ${clients.size}",
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
                        // QR Pairing Code Button
                        IconButton(onClick = { showHostQrDialog = true }) {
                            Icon(Icons.Filled.QrCode, contentDescription = "Show Host QR", tint = Color.White)
                        }
                        // Layout Grid Icon Button (to the right of QR button)
                        Box {
                            IconButton(onClick = { showGridDropdown = true }) {
                                Icon(Icons.Filled.GridView, contentDescription = "Layout Grid", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showGridDropdown,
                                onDismissRequest = { showGridDropdown = false },
                                modifier = Modifier.background(GrayCard)
                            ) {
                                listOf(4, 6, 9, 12, 16).forEach { size ->
                                    val isSelected = selectedGridSize == size
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Client x$size",
                                                color = if (isSelected) RedPrimary else Color.White,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                        },
                                        onClick = {
                                            selectedGridSize = size
                                            showGridDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBackground)
                )
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = BlackBackground,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Record All Button
                    Button(
                        onClick = {
                            isRecordingAll = !isRecordingAll
                            val cmd = if (isRecordingAll) "START_RECORDING" else "STOP_RECORDING"
                            viewModel.triggerRemoteCommandToAll(
                                ControlCommand(command = cmd, intValue = 900)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecordingAll) RedDark else RedPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("record_all_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingAll) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = "Record All",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isRecordingAll) "STOP ALL" else "RECORD ALL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        containerColor = BlackBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            if (clients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(RedPrimary.copy(alpha = 0.05f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Tv, contentDescription = "Monitor", tint = RedPrimary, modifier = Modifier.size(32.dp))
                        }
                        Text(
                            "Awaiting Camera Client Connections...",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Launch 'Client Camera' mode on other devices\non your local Wi-Fi Hotspot network.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val hostIp by viewModel.clientIp.collectAsStateWithLifecycle()
                        Text(
                            "Host IP Address: $hostIp",
                            color = RedPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Live View Camera Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(clients.take(selectedGridSize), key = { it.deviceId }) { client ->
                        val frame = clientFrames[client.deviceId]
                        CameraPreviewTile(
                            client = client,
                            frame = frame,
                            onDoubleTap = {
                                fullscreenClient = client
                            },
                            onLongPress = {
                                selectedClientForControl = client
                            }
                        )
                    }
                }
            }
        }

        // Fullscreen preview mode
        fullscreenClient?.let { currentClient ->
            // Re-fetch latest status in case it changed
            val clientRef = clients.find { it.deviceId == currentClient.deviceId } ?: currentClient
            val frame = clientFrames[clientRef.deviceId]
            
            FullscreenPreviewDialog(
                client = clientRef,
                frame = frame,
                onDismiss = { fullscreenClient = null },
                onZoomChange = { zoomVal ->
                    viewModel.triggerRemoteCommand(clientRef.deviceId, ControlCommand("SET_ZOOM", floatValue = zoomVal))
                },
                onFlashToggle = {
                    viewModel.triggerRemoteCommand(clientRef.deviceId, ControlCommand("TOGGLE_FLASH"))
                }
            )
        }

        // Selected client control panel drawer
        selectedClientForControl?.let { currentClient ->
            val clientRef = clients.find { it.deviceId == currentClient.deviceId } ?: currentClient
            ClientControlDrawer(
                client = clientRef,
                onDismiss = { selectedClientForControl = null },
                onTriggerCommand = { cmd ->
                    viewModel.triggerRemoteCommand(clientRef.deviceId, cmd)
                }
            )
        }

        // Host QR Code Dialog
        if (showHostQrDialog) {
            val hostIp by viewModel.clientIp.collectAsStateWithLifecycle()
            val pairingText = "live_camera_host:$hostIp"
            val qrBitmap = remember(pairingText) {
                QrCodeUtils.generateQrCode(pairingText, 300)
            }

            AlertDialog(
                onDismissRequest = { showHostQrDialog = false },
                title = {
                    Text(
                        "HOST PAIRING QR CODE",
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
                            "Scan this QR code with the Client Camera to connect directly to this Host Console.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        
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
                                    contentDescription = "Host Pairing QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Text(
                            "Host IP Address: $hostIp",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHostQrDialog = false }) {
                        Text("CLOSE", color = RedPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = GraySurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraPreviewTile(
    client: ConnectedClientInfo,
    frame: Bitmap?,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val isRecording = client.status.isRecording
    val borderColor = if (isRecording) RedPrimary else Color.White.copy(alpha = 0.12f)
    val borderWidth = if (isRecording) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .aspectRatio(9f / 16f) // Taller aspect ratio for perfect vertical camera views
            .border(
                borderWidth,
                borderColor,
                RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onLongPress, // Clicking opens control
                onDoubleClick = onDoubleTap,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GrayCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (frame != null) {
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = "Live feed from ${client.deviceName}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = RedPrimary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "LOADING STREAM...",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Dark gradient overlay on top for indicators
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
            )

            // Top Indicators Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Name Pill (Glassmorphic)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (client.isConnected) GreenActive else TextSecondary, CircleShape)
                    )
                    Text(
                        client.deviceName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 85.dp)
                    )
                }

                // Battery / FPS Status Pill (Glassmorphic)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${client.status.currentResolution} @ ${client.status.currentFps}fps",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(
                            Icons.Filled.BatteryChargingFull,
                            contentDescription = "Battery",
                            tint = if (client.status.batteryLevel < 20) RedPrimary else GreenActive,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            "${client.status.batteryLevel}%",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom controls overlay gradient
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )

            // Bottom controls overlay elements
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Recording Indicator
                    if (isRecording) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedPrimary.copy(alpha = 0.25f))
                                .border(1.dp, RedPrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(RedPrimary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "REC",
                                color = RedPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(GreenActive.copy(alpha = 0.15f))
                                .border(1.dp, GreenActive.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GreenActive, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "STANDBY",
                                color = GreenActive,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Network speed (Glassmorphic Badge)
                    Text(
                        "${String.format("%.1f", client.networkSpeedKbps)} Kbps",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FullscreenPreviewDialog(
    client: ConnectedClientInfo,
    frame: Bitmap?,
    onDismiss: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onFlashToggle: () -> Unit
) {
    var zoomValue by remember { mutableStateOf(client.status.zoom) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Fullscreen feed",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Overlay layout controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        client.deviceName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onFlashToggle,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (client.status.flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = "Flash",
                            tint = if (client.status.flashOn) RedPrimary else Color.White
                        )
                    }
                }
            }

            // Bottom Zoom slider overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ZOOM CONTROL", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${String.format("%.1f", zoomValue)}x", color = RedPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = zoomValue,
                    onValueChange = {
                        zoomValue = it
                        onZoomChange(it)
                    },
                    valueRange = 1.0f..10.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = RedPrimary,
                        activeTrackColor = RedPrimary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ClientControlDrawer(
    client: ConnectedClientInfo,
    onDismiss: () -> Unit,
    onTriggerCommand: (ControlCommand) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "CAMERA CONTROLS: ${client.deviceName}",
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Flash / Facing / Recording switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Record Toggle Button
                    Button(
                        onClick = {
                            val cmd = if (client.status.isRecording) "STOP_RECORDING" else "START_RECORDING"
                            onTriggerCommand(ControlCommand(command = cmd, intValue = 900))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (client.status.isRecording) RedDark else RedPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).padding(end = 4.dp).testTag("dialog_record_toggle")
                    ) {
                        Icon(
                            imageVector = if (client.status.isRecording) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = "Record"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (client.status.isRecording) "Stop" else "Record", fontSize = 12.sp)
                    }

                    // Flip Camera Button
                    Button(
                        onClick = { onTriggerCommand(ControlCommand("SWITCH_CAMERA")) },
                        colors = ButtonDefaults.buttonColors(containerColor = GrayCard),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("dialog_flip_camera")
                    ) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Flip Camera")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flip", fontSize = 12.sp)
                    }
                }

                // Slider for Zoom Control
                var currentZoom by remember { mutableStateOf(client.status.zoom) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Zoom level", color = TextSecondary, fontSize = 12.sp)
                        Text("${String.format("%.1f", currentZoom)}x", color = RedPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = currentZoom,
                        onValueChange = {
                            currentZoom = it
                            onTriggerCommand(ControlCommand("SET_ZOOM", floatValue = it))
                        },
                        valueRange = 1.0f..10.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = RedPrimary,
                            activeTrackColor = RedPrimary
                        )
                    )
                }

                // Dynamic client options (Battery Saver & Silent Mode)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Battery Saver
                    Button(
                        onClick = { onTriggerCommand(ControlCommand("SET_BATTERY_SAVER", boolValue = !client.status.batterySaver)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (client.status.batterySaver) RedPrimary.copy(alpha = 0.15f) else GrayCard
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (client.status.batterySaver) RedPrimary else Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.BatteryChargingFull,
                            contentDescription = "Saver",
                            tint = if (client.status.batterySaver) RedPrimary else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dimmer", fontSize = 11.sp, color = if (client.status.batterySaver) RedPrimary else Color.White)
                    }

                    // Silent Mode
                    Button(
                        onClick = { onTriggerCommand(ControlCommand("SET_SILENT_MODE", boolValue = !client.status.silentMode)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (client.status.silentMode) RedPrimary.copy(alpha = 0.15f) else GrayCard
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (client.status.silentMode) RedPrimary else Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.VolumeMute,
                            contentDescription = "Silent",
                            tint = if (client.status.silentMode) RedPrimary else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mute", fontSize = 11.sp, color = if (client.status.silentMode) RedPrimary else Color.White)
                    }
                }

                // Resolution Selection Options
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("STREAM RESOLUTION", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("480p", "720p", "1080p").forEach { res ->
                            val isSel = client.status.currentResolution == res
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) RedPrimary else GrayCard)
                                    .clickable { onTriggerCommand(ControlCommand("SET_RESOLUTION", stringValue = res)) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    res.uppercase(),
                                    color = if (isSel) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // FPS Selector Options
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("TARGET FPS LIMIT", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(30, 60).forEach { fps ->
                            val isSel = client.status.currentFps == fps
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) RedPrimary else GrayCard)
                                    .clickable { onTriggerCommand(ControlCommand("SET_FPS", intValue = fps)) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$fps FPS",
                                    color = if (isSel) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DONE", color = RedPrimary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = GraySurface,
        shape = RoundedCornerShape(16.dp)
    )
}
