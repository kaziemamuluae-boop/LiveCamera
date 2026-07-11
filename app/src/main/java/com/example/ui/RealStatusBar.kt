package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RealStatusBar(pulseAlpha: Float) {
    val context = LocalContext.current
    
    // Synchronously get initial current time
    var currentTime by remember { 
        mutableStateOf(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())) 
    }
    
    // Synchronously get initial battery level
    val initialBattery = remember {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (batteryManager != null) {
                val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (capacity in 0..100) capacity else 100
            } else {
                100
            }
        } catch (e: Exception) {
            100
        }
    }
    var batteryPercentage by remember { mutableStateOf(initialBattery) }

    // Tick time every second
    LaunchedEffect(Unit) {
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        while (true) {
            currentTime = formatter.format(Date())
            delay(1000)
        }
    }

    // Read battery percentage periodically
    LaunchedEffect(Unit) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        while (true) {
            try {
                if (batteryManager != null) {
                    val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    if (capacity in 0..100) {
                        batteryPercentage = capacity
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
            delay(10000) // update every 10 seconds
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = currentTime,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(RedDark.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .alpha(pulseAlpha)
                        .background(RedPrimary, CircleShape)
                )
                Text(
                    text = "OFFLINE HOTSPOT",
                    color = RedPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = "$batteryPercentage%",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
