package com.example.network

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue

class ClientConnection {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val commandAdapter = moshi.adapter(ControlCommand::class.java)
    private val statusAdapter = moshi.adapter(ClientStatus::class.java)

    private var socket: Socket? = null
    private var dataOutputStream: DataOutputStream? = null
    private var connectionJob: Job? = null
    
    private val frameQueue = LinkedBlockingQueue<ByteArray>(2) // Limit queue to prevent OOM
    private val _commandsFlow = MutableSharedFlow<ControlCommand>(extraBufferCapacity = 64)
    val commandsFlow: SharedFlow<ControlCommand> = _commandsFlow

    var isConnected = false
        private set

    companion object {
        private const val TAG = "ClientConnection"
        private const val PORT = 9000
    }

    fun start(scope: CoroutineScope, hostIp: String, statusProvider: () -> ClientStatus) {
        stop()
        connectionJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    Log.d(TAG, "Connecting to Host at $hostIp:$PORT...")
                    val s = Socket(hostIp, PORT).apply {
                        tcpNoDelay = true
                        sendBufferSize = 64 * 1024
                        receiveBufferSize = 16 * 1024
                    }
                    socket = s
                    isConnected = true
                    Log.i(TAG, "Connected to Host!")

                    val dis = DataInputStream(BufferedInputStream(s.getInputStream()))
                    val dos = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                    dataOutputStream = dos

                    // 1. Reader loop for commands from Host
                    val readerJob = launch {
                        try {
                            while (isActive) {
                                val type = dis.readByte()
                                val length = dis.readInt()
                                if (length <= 0 || length > 1024 * 1024) continue // Safety limit
                                val payload = ByteArray(length)
                                dis.readFully(payload)

                                if (type == 0x03.toByte()) {
                                    val json = String(payload, Charsets.UTF_8)
                                    val command = commandAdapter.fromJson(json)
                                    if (command != null) {
                                        Log.d(TAG, "Received remote command: $command")
                                        _commandsFlow.emit(command)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Reader loop disconnected: ${e.message}")
                        }
                    }

                    // 2. Sender loop for status & frames
                    val senderJob = launch {
                        var lastStatusTime = 0L
                        try {
                            while (isActive) {
                                val currentTime = System.currentTimeMillis()
                                
                                // Send status update every second
                                if (currentTime - lastStatusTime > 1000) {
                                    val status = statusProvider()
                                    val json = statusAdapter.toJson(status)
                                    val bytes = json.toByteArray(Charsets.UTF_8)
                                    
                                    synchronized(dos) {
                                        dos.writeByte(0x02) // Status packet
                                        dos.writeInt(bytes.size)
                                        dos.write(bytes)
                                        dos.flush()
                                    }
                                    lastStatusTime = currentTime
                                }

                                // Send available frames
                                val frame = frameQueue.poll()
                                if (frame != null) {
                                    synchronized(dos) {
                                        dos.writeByte(0x01) // Frame packet
                                        dos.writeInt(frame.size)
                                        dos.write(frame)
                                        dos.flush()
                                    }
                                } else {
                                    delay(10) // Small sleep to prevent tight loop
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Sender loop disconnected: ${e.message}")
                        }
                    }

                    // Wait for either loop to fail
                    joinAll(readerJob, senderJob)

                } catch (e: Exception) {
                    Log.e(TAG, "Connection failed, retrying in 3 seconds: ${e.message}")
                    isConnected = false
                    delay(3000)
                } finally {
                    cleanupSocket()
                }
            }
        }
    }

    fun sendFrame(jpegBytes: ByteArray) {
        if (!isConnected) return
        // Offer to queue, if queue is full, evict oldest to avoid lag
        if (!frameQueue.offer(jpegBytes)) {
            frameQueue.poll()
            frameQueue.offer(jpegBytes)
        }
    }

    private fun cleanupSocket() {
        isConnected = false
        dataOutputStream = null
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        socket = null
        frameQueue.clear()
    }

    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        cleanupSocket()
    }
}
