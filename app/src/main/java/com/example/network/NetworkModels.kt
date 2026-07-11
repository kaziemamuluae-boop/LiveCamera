package com.example.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClientStatus(
    val deviceId: String,
    val deviceName: String,
    val ipAddress: String,
    val batteryLevel: Int = 100,
    val isRecording: Boolean = false,
    val currentFacing: String = "REAR",
    val currentResolution: String = "720p",
    val currentFps: Int = 30,
    val flashOn: Boolean = false,
    val zoom: Float = 1.0f,
    val batterySaver: Boolean = false,
    val silentMode: Boolean = false,
    val cameraLocked: Boolean = false,
    val screenBrightness: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
data class ControlCommand(
    val command: String,
    val stringValue: String? = null,
    val boolValue: Boolean? = null,
    val floatValue: Float? = null,
    val intValue: Int? = null
)
