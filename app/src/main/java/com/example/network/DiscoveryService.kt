package com.example.network

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

class DiscoveryService {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val statusAdapter = moshi.adapter(ClientStatus::class.java)

    private val broadcastPort = 9001
    private var broadcastJob: Job? = null
    private var listenerJob: Job? = null
    private var broadcastSocket: DatagramSocket? = null
    private var listenerSocket: DatagramSocket? = null

    companion object {
        private const val TAG = "DiscoveryService"
    }

    fun startBroadcasting(scope: CoroutineScope, statusProvider: () -> ClientStatus) {
        stopBroadcasting()
        broadcastJob = scope.launch(Dispatchers.IO) {
            try {
                broadcastSocket = DatagramSocket().apply {
                    broadcast = true
                }
                while (isActive) {
                    val status = statusProvider()
                    val json = statusAdapter.toJson(status)
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    
                    // Broadcast to standard IPv4 broadcast address
                    val address = InetAddress.getByName("255.255.255.255")
                    val packet = DatagramPacket(bytes, bytes.size, address, broadcastPort)
                    
                    try {
                        broadcastSocket?.send(packet)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending broadcast packet: ${e.message}")
                    }
                    delay(1500) // Broadcast every 1.5 seconds
                }
            } catch (e: Exception) {
                Log.e(TAG, "Broadcasting error: ${e.message}")
            } finally {
                broadcastSocket?.close()
                broadcastSocket = null
            }
        }
    }

    fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null
        try {
            broadcastSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        broadcastSocket = null
    }

    fun startListening(scope: CoroutineScope, onDeviceDiscovered: (ClientStatus) -> Unit) {
        stopListening()
        listenerJob = scope.launch(Dispatchers.IO) {
            try {
                // Bind socket to reuse address and listen on broadcastPort
                listenerSocket = DatagramSocket(broadcastPort).apply {
                    reuseAddress = true
                }
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        listenerSocket?.receive(packet)
                        val data = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        
                        // Parse status
                        val status = statusAdapter.fromJson(data)
                        if (status != null) {
                            // Automatically override the IP address with the packet's sender IP address
                            val statusWithRealIp = status.copy(ipAddress = packet.address.hostAddress ?: status.ipAddress)
                            withContext(Dispatchers.Main) {
                                onDeviceDiscovered(statusWithRealIp)
                            }
                        }
                    } catch (e: SocketException) {
                        Log.d(TAG, "Socket closed or exception: ${e.message}")
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Error receiving packet: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Listening error: ${e.message}")
            } finally {
                listenerSocket?.close()
                listenerSocket = null
            }
        }
    }

    fun stopListening() {
        listenerJob?.cancel()
        listenerJob = null
        try {
            listenerSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        listenerSocket = null
    }
}
