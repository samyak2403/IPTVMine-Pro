package com.samyak.iptvminepro.ui.screens.settings

import android.net.wifi.WifiManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.provider.ProviderPairingServer
import com.samyak.iptvminepro.ui.theme.ColorPrimaryDark
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var pairingCode by remember { mutableStateOf("-------") }
    var secondsRemaining by remember { mutableStateOf(30) }
    var pairedTvDevice by remember { mutableStateOf<String?>(null) }
    
    val localIp = remember { getLocalIpAddress() }
    val wifiSsid = remember { getWifiSSID(context) }

    // Initialize and start pairing server
    val pairingServer = remember {
        ProviderPairingServer(
            context = context,
            onCodeUpdated = { code -> pairingCode = code },
            onTimeUpdated = { time -> secondsRemaining = time },
            onPairingSuccess = { deviceName -> pairedTvDevice = deviceName }
        )
    }

    DisposableEffect(Unit) {
        pairingServer.start()
        onDispose {
            pairingServer.stop()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Device Icon / Status Indicator
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            if (pairedTvDevice != null) Color(0xFF00C853).copy(alpha = 0.15f)
                            else Color(0xFF26A69A).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (pairedTvDevice != null) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(44.dp)
                        )
                    } else {
                        val infiniteTransition = rememberInfiniteTransition()
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.9f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        Icon(
                            imageVector = Icons.Filled.Tv,
                            contentDescription = "TV Pairing",
                            tint = Color(0xFF26A69A),
                            modifier = Modifier
                                .size(40.dp)
                                .scale(pulseScale)
                        )
                    }
                }

                if (pairedTvDevice != null) {
                    // Success State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Paired Successfully!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00C853)
                        )
                        Text(
                            text = "Active providers have been shared with:\n$pairedTvDevice",
                            fontSize = 15.sp,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                        ) {
                            Text("Done", color = Color.White)
                        }
                    }
                } else {
                    // Pairing Code Display State
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Mobile App Provider Mode",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Enter the 7-digit code below on your TV app.",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Large 7-digit code box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp, horizontal = 16.dp)
                        ) {
                            // Separated digits formatting
                            val formattedCode = if (pairingCode.length == 7) {
                                "${pairingCode.substring(0, 3)} ${pairingCode.substring(3)}"
                            } else {
                                pairingCode
                            }

                            Text(
                                text = formattedCode,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF26A69A),
                                letterSpacing = 4.sp,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))

                            // Timer countdown row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                CircularProgressIndicator(
                                    progress = { secondsRemaining / 30f },
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF26A69A),
                                    strokeWidth = 2.dp,
                                    trackColor = Color(0xFFE2E8F0)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Code rotates in ${secondsRemaining}s",
                                    fontSize = 13.sp,
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Wi-Fi and network details
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = "Wi-Fi Network", tint = Color(0xFF26A69A), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Wi-Fi network requirements:", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                text = "1. Ensure both your Phone and TV are connected to the same Wi-Fi router.",
                                color = Color(0xFF475569),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "2. Open the 'Playlists' screen on the TV app and click 'Import from Phone'.",
                                color = Color(0xFF475569),
                                fontSize = 12.sp
                            )
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Wi-Fi SSID", color = Color(0xFF64748B), fontSize = 12.sp)
                                Text(wifiSsid, color = Color(0xFF0F172A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Local IP Address", color = Color(0xFF64748B), fontSize = 12.sp)
                                Text(localIp, color = Color(0xFF0F172A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getLocalIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    return address.hostAddress ?: "Unknown"
                }
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
    return "Unknown"
}

private fun getWifiSSID(context: Context): String {
    return try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        if (info != null) {
            val ssid = info.ssid
            if (ssid != null && ssid != "<unknown ssid>") {
                ssid.replace("\"", "")
            } else {
                "Connected Local Wi-Fi"
            }
        } else {
            "Local Network"
        }
    } catch (e: Exception) {
        "Local Network"
    }
}
