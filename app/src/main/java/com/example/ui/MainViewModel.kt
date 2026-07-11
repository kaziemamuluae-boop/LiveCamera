package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.BatteryManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.CameraManager
import com.example.data.LiveCameraDatabase
import com.example.data.RecordingEntity
import com.example.data.RecordingRepository
import com.example.network.*
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.ConcurrentHashMap

enum class AppRole {
    NONE, HOST, CLIENT
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = LiveCameraDatabase.getDatabase(application)
    private val repository = RecordingRepository(database.recordingDao())
    val cameraManager = CameraManager(application)
    
    private val discoveryService = DiscoveryService()
    private val hostServer = HostServer()
    private val clientConnection = ClientConnection()
    
    // UI state flows
    private val _role = MutableStateFlow(AppRole.NONE)
    val role: StateFlow<AppRole> = _role.asStateFlow()

    // Host screen states
    val connectedClients: StateFlow<List<ConnectedClientInfo>> = hostServer.connectedClients
    private val _clientFramesState = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val clientFramesState: StateFlow<Map<String, Bitmap>> = _clientFramesState.asStateFlow()

    // Client screen states
    private val _clientFacing = MutableStateFlow("REAR")
    val clientFacing: StateFlow<String> = _clientFacing.asStateFlow()

    private val _clientResolution = MutableStateFlow("720p")
    val clientResolution: StateFlow<String> = _clientResolution.asStateFlow()

    private val _clientFps = MutableStateFlow(30)
    val clientFps: StateFlow<Int> = _clientFps.asStateFlow()

    private val _flashOn = MutableStateFlow(false)
    val flashOn: StateFlow<Boolean> = _flashOn.asStateFlow()

    private val _zoom = MutableStateFlow(1.0f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _batterySaver = MutableStateFlow(false)
    val batterySaver: StateFlow<Boolean> = _batterySaver.asStateFlow()

    private val _silentMode = MutableStateFlow(false)
    val silentMode: StateFlow<Boolean> = _silentMode.asStateFlow()

    private val _cameraLocked = MutableStateFlow(false)
    val cameraLocked: StateFlow<Boolean> = _cameraLocked.asStateFlow()

    private val _screenBrightness = MutableStateFlow(1.0f)
    val screenBrightness: StateFlow<Float> = _screenBrightness.asStateFlow()

    private val _isRecordingLocally = MutableStateFlow(false)
    val isRecordingLocally: StateFlow<Boolean> = _isRecordingLocally.asStateFlow()

    private val _clientIp = MutableStateFlow("127.0.0.1")
    val clientIp: StateFlow<String> = _clientIp.asStateFlow()

    private val _isClientConnectedToHost = MutableStateFlow(false)
    val isClientConnectedToHost: StateFlow<Boolean> = _isClientConnectedToHost.asStateFlow()

    // History & settings
    val recordingsHistory: StateFlow<List<RecordingEntity>> = repository.allRecordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Split settings (in seconds)
    private val _splitIntervalSeconds = MutableStateFlow(900) // 15 mins default
    val splitIntervalSeconds: StateFlow<Int> = _splitIntervalSeconds.asStateFlow()

    private var clientJobs: Job? = null
    private var hostJobs: Job? = null
    private var splitTimerJob: Job? = null
    private var recordingStartTime = 0L
    private val deviceId = UUID.randomUUID().toString().substring(0, 8)
    private val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    init {
        _clientIp.value = getLocalIpAddress()
        
        // Monitor client frame updates periodically
        viewModelScope.launch {
            while (isActive) {
                if (_role.value == AppRole.HOST) {
                    _clientFramesState.value = hostServer.clientFrames.toMap()
                }
                delay(33) // ~30fps poll for UI update
            }
        }
    }

    fun selectRole(role: AppRole) {
        cleanupAll()
        _role.value = role
        if (role == AppRole.HOST) {
            startHostMode()
        } else if (role == AppRole.CLIENT) {
            startClientMode()
        }
    }

    private fun startHostMode() {
        hostServer.startServer(viewModelScope)
        discoveryService.startListening(viewModelScope) { clientStatus ->
            // Update UI with newly discovered client or connection states
        }
    }

    private fun startClientMode() {
        _clientIp.value = getLocalIpAddress()
        
        // Listen to remote commands from Host
        viewModelScope.launch {
            clientConnection.commandsFlow.collect { command ->
                handleRemoteCommand(command)
            }
        }

        // Start broadcasting client presence via UDP
        discoveryService.startBroadcasting(viewModelScope) {
            getClientStatus()
        }
    }

    fun connectClientToHost(hostIpAddress: String) {
        clientConnection.start(viewModelScope, hostIpAddress) {
            getClientStatus()
        }
        
        viewModelScope.launch {
            while (isActive) {
                _isClientConnectedToHost.value = clientConnection.isConnected
                delay(1000)
            }
        }
    }

    private fun getClientStatus(): ClientStatus {
        val context = getApplication<Application>()
        var batteryPct = 100
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (batteryManager != null) {
                val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (capacity in 0..100) {
                    batteryPct = capacity
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        return ClientStatus(
            deviceId = deviceId,
            deviceName = deviceName,
            ipAddress = _clientIp.value,
            batteryLevel = batteryPct,
            isRecording = _isRecordingLocally.value,
            currentFacing = _clientFacing.value,
            currentResolution = _clientResolution.value,
            currentFps = _clientFps.value,
            flashOn = _flashOn.value,
            zoom = _zoom.value,
            batterySaver = _batterySaver.value,
            silentMode = _silentMode.value,
            cameraLocked = _cameraLocked.value,
            screenBrightness = _screenBrightness.value,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun handleRemoteCommand(command: ControlCommand) {
        when (command.command) {
            "START_RECORDING" -> {
                val interval = command.intValue ?: 900
                _splitIntervalSeconds.value = interval
                startLocalRecording()
            }
            "STOP_RECORDING" -> stopLocalRecording()
            "TOGGLE_FLASH" -> {
                val enable = command.boolValue ?: !_flashOn.value
                _flashOn.value = enable
                cameraManager.setFlash(enable)
            }
            "SWITCH_CAMERA" -> {
                val facing = command.stringValue ?: if (_clientFacing.value == "REAR") "FRONT" else "REAR"
                _clientFacing.value = facing
                _flashOn.value = false // Flash typically resets
            }
            "SET_ZOOM" -> {
                val z = command.floatValue ?: 1.0f
                _zoom.value = z
                cameraManager.setZoom(z)
            }
            "SET_BATTERY_SAVER" -> {
                _batterySaver.value = command.boolValue ?: !_batterySaver.value
            }
            "SET_SILENT_MODE" -> {
                _silentMode.value = command.boolValue ?: !_silentMode.value
            }
            "SET_CAMERA_LOCK" -> {
                _cameraLocked.value = command.boolValue ?: !_cameraLocked.value
            }
            "SET_SCREEN_BRIGHTNESS" -> {
                _screenBrightness.value = command.floatValue ?: 1.0f
            }
            "SET_RESOLUTION" -> {
                _clientResolution.value = command.stringValue ?: "720p"
            }
            "SET_FPS" -> {
                _clientFps.value = command.intValue ?: 30
            }
        }
    }

    // Host remote triggers
    fun triggerRemoteCommand(deviceId: String, command: ControlCommand) {
        hostServer.sendControlCommand(deviceId, command)
    }

    fun triggerRemoteCommandToAll(command: ControlCommand) {
        hostServer.sendControlCommandToAll(command)
    }

    // Client local controls
    fun setLocalZoom(ratio: Float) {
        _zoom.value = ratio
        cameraManager.setZoom(ratio)
    }

    fun toggleLocalFlash() {
        val enable = !_flashOn.value
        _flashOn.value = enable
        cameraManager.setFlash(enable)
    }

    fun toggleLocalCameraFacing() {
        val facing = if (_clientFacing.value == "REAR") "FRONT" else "REAR"
        _clientFacing.value = facing
        _flashOn.value = false
    }

    fun setSplitInterval(seconds: Int) {
        _splitIntervalSeconds.value = seconds
    }

    fun startLocalRecording() {
        if (_isRecordingLocally.value) return
        val context = getApplication<Application>()
        val appName = try {
            context.getString(context.applicationInfo.labelRes)
        } catch (e: Exception) {
            "Live Camera"
        }
        val publicMoviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appDir = File(publicMoviesDir, appName)
        val outputDir = if (appDir.exists() || appDir.mkdirs()) {
            appDir
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        }
        
        recordingStartTime = System.currentTimeMillis()
        val file = cameraManager.startRecording(outputDir) { event ->
            // Process record events if needed
        }

        if (file != null) {
            _isRecordingLocally.value = true
            startSplitTimer(outputDir)
        }
    }

    private fun startSplitTimer(outputDir: File) {
        splitTimerJob?.cancel()
        splitTimerJob = viewModelScope.launch(Dispatchers.IO) {
            var elapsed = 0
            while (isActive && _isRecordingLocally.value) {
                delay(1000)
                elapsed++
                if (elapsed >= _splitIntervalSeconds.value) {
                    Log.i("MainViewModel", "Auto split triggered after ${elapsed}s")
                    // Stop current recording and save to history
                    val savedFile = cameraManager.currentRecordingFile
                    withContext(Dispatchers.Main) {
                        cameraManager.stopRecording()
                        _isRecordingLocally.value = false
                        saveRecordingToDb(savedFile, elapsed.toLong())
                        
                        // Restart recording immediately for seamless split
                        delay(200)
                        startLocalRecording()
                    }
                    break
                }
            }
        }
    }

    fun stopLocalRecording() {
        if (!_isRecordingLocally.value) return
        splitTimerJob?.cancel()
        splitTimerJob = null
        
        val durationSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000
        val savedFile = cameraManager.currentRecordingFile
        
        cameraManager.stopRecording()
        _isRecordingLocally.value = false
        
        viewModelScope.launch {
            saveRecordingToDb(savedFile, durationSeconds)
        }
    }

    private fun saveRecordingToDb(file: File?, durationSec: Long) {
        if (file == null || !file.exists()) return
        viewModelScope.launch(Dispatchers.IO) {
            val entity = RecordingEntity(
                fileName = file.name,
                filePath = file.absolutePath,
                cameraName = deviceName,
                durationSeconds = durationSec,
                fileSize = file.length(),
                resolution = _clientResolution.value
            )
            repository.insertRecording(entity)
            Log.i("MainViewModel", "Recording log saved to DB: ${file.name}")
        }
    }

    fun deleteRecordingLog(id: Int, filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRecording(id)
            try {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete file: ${e.message}")
            }
        }
    }

    fun clearAllRecordingsHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    fun sendLocalFrame(jpegBytes: ByteArray) {
        if (_role.value == AppRole.CLIENT && _isClientConnectedToHost.value) {
            clientConnection.sendFrame(jpegBytes)
        }
    }

    private fun cleanupAll() {
        // Stop Host components
        hostServer.stopServer()
        discoveryService.stopListening()

        // Stop Client components
        clientConnection.stop()
        discoveryService.stopBroadcasting()
        cameraManager.stopRecording()
        
        splitTimerJob?.cancel()
        splitTimerJob = null
        _isRecordingLocally.value = false
        _isClientConnectedToHost.value = false
    }

    override fun onCleared() {
        super.onCleared()
        cleanupAll()
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: continue
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("MainViewModel", "Error getting local IP: ${ex.message}")
        }
        return "127.0.0.1"
    }
}
