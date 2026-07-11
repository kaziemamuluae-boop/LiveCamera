package com.example.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap

data class ConnectedClientInfo(
    val deviceId: String,
    val deviceName: String,
    val ipAddress: String,
    var status: ClientStatus,
    var networkSpeedKbps: Float = 0f,
    var isConnected: Boolean = true,
    var latencyMs: Long = 0
)

class HostServer {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val commandAdapter = moshi.adapter(ControlCommand::class.java)
    private val statusAdapter = moshi.adapter(ClientStatus::class.java)

    private val port = 9000
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var speedMonitorJob: Job? = null

    // Sockets and streams for active clients
    private val clientsMap = ConcurrentHashMap<String, ActiveClient>()
    
    // UI state flows
    private val _connectedClients = MutableStateFlow<List<ConnectedClientInfo>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClientInfo>> = _connectedClients.asStateFlow()

    // Store latest frames for each client ID
    private val _clientFrames = ConcurrentHashMap<String, Bitmap>()
    val clientFrames: Map<String, Bitmap> get() = _clientFrames

    companion object {
        private const val TAG = "HostServer"
    }

    private class ActiveClient(
        val socket: Socket,
        val dis: DataInputStream,
        val dos: DataOutputStream,
        var info: ConnectedClientInfo,
        var bytesReadSinceLastCheck: Long = 0,
        var lastReceivedTime: Long = System.currentTimeMillis()
    )

    fun startServer(scope: CoroutineScope) {
        stopServer()
        _clientFrames.clear()
        
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "Host TCP Server started on port $port")

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.tcpNoDelay = true
                    clientSocket.receiveBufferSize = 64 * 1024
                    clientSocket.sendBufferSize = 16 * 1024
                    
                    launchClientHandler(scope, clientSocket)
                }
            } catch (e: SocketException) {
                Log.d(TAG, "Server socket closed: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }

        // Network throughput monitor (runs every 1 second)
        speedMonitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                clientsMap.values.forEach { client ->
                    // Calculate KB/s
                    client.info.networkSpeedKbps = (client.bytesReadSinceLastCheck / 1024f) * 8f // Kbps
                    client.bytesReadSinceLastCheck = 0
                    
                    // Mark disconnected if inactive for more than 5 seconds
                    if (System.currentTimeMillis() - client.lastReceivedTime > 5000) {
                        client.info.isConnected = false
                    }
                }
                updateClientsList()
            }
        }
    }

    private fun launchClientHandler(scope: CoroutineScope, socket: Socket) {
        scope.launch(Dispatchers.IO) {
            var deviceId: String? = null
            try {
                val dis = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val clientIp = socket.inetAddress.hostAddress ?: "Unknown"

                while (isActive) {
                    val type = dis.readByte()
                    val length = dis.readInt()
                    if (length <= 0 || length > 10 * 1024 * 1024) continue // Limit to 10MB safety

                    val payload = ByteArray(length)
                    dis.readFully(payload)

                    val activeClient = deviceId?.let { clientsMap[it] }
                    if (activeClient != null) {
                        activeClient.bytesReadSinceLastCheck += length + 5 // Packet header + content
                        activeClient.lastReceivedTime = System.currentTimeMillis()
                    }

                    when (type) {
                        0x02.toByte() -> { // Client Status JSON
                            val json = String(payload, Charsets.UTF_8)
                            val status = statusAdapter.fromJson(json)
                            if (status != null) {
                                val currentId = status.deviceId
                                if (deviceId == null) {
                                    deviceId = currentId
                                }

                                val info = ConnectedClientInfo(
                                    deviceId = status.deviceId,
                                    deviceName = status.deviceName,
                                    ipAddress = clientIp,
                                    status = status,
                                    isConnected = true,
                                    latencyMs = System.currentTimeMillis() - status.timestamp
                                )

                                val existingClient = clientsMap[currentId]
                                if (existingClient != null) {
                                    existingClient.info = info
                                    existingClient.lastReceivedTime = System.currentTimeMillis()
                                } else {
                                    clientsMap[currentId] = ActiveClient(
                                        socket = socket,
                                        dis = dis,
                                        dos = dos,
                                        info = info
                                    )
                                }
                                updateClientsList()
                            }
                        }
                        0x01.toByte() -> { // Video Frame (JPEG)
                            if (deviceId != null) {
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = 2 // Decodes at half width & height, making it 4x faster and using 4x less memory
                                }
                                val decoded = BitmapFactory.decodeByteArray(payload, 0, payload.size, options)
                                if (decoded != null) {
                                    val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                                    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                                    if (rotated != decoded) {
                                        decoded.recycle()
                                    }
                                    _clientFrames[deviceId!!] = rotated
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Client handler disconnected (${socket.inetAddress.hostAddress}): ${e.message}")
            } finally {
                deviceId?.let { id ->
                    clientsMap[id]?.let { client ->
                        client.info.isConnected = false
                        try {
                            client.socket.close()
                        } catch (ex: Exception) {}
                    }
                    clientsMap.remove(id)
                    _clientFrames.remove(id)
                }
                try {
                    socket.close()
                } catch (e: Exception) {}
                updateClientsList()
            }
        }
    }

    private fun updateClientsList() {
        val list = clientsMap.values.map { it.info }.sortedBy { it.deviceName }
        _connectedClients.value = list
    }

    fun sendControlCommand(deviceId: String, command: ControlCommand) {
        val client = clientsMap[deviceId]
        if (client != null && client.info.isConnected) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = commandAdapter.toJson(command)
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    synchronized(client.dos) {
                        client.dos.writeByte(0x03) // Command header
                        client.dos.writeInt(bytes.size)
                        client.dos.write(bytes)
                        client.dos.flush()
                    }
                    Log.d(TAG, "Sent command to $deviceId: $command")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send command to $deviceId: ${e.message}")
                }
            }
        }
    }

    fun sendControlCommandToAll(command: ControlCommand) {
        clientsMap.keys.forEach { deviceId ->
            sendControlCommand(deviceId, command)
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        serverJob = null
        speedMonitorJob?.cancel()
        speedMonitorJob = null

        clientsMap.values.forEach { client ->
            try {
                client.socket.close()
            } catch (e: Exception) {}
        }
        clientsMap.clear()
        _clientFrames.clear()
        _connectedClients.value = emptyList()
        
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        Log.i(TAG, "Host TCP Server stopped")
    }
}
