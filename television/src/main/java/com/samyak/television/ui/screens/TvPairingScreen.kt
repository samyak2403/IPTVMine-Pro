package com.samyak.television.ui.screens

import android.net.nsd.NsdServiceInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.google.gson.Gson
import com.samyak.television.data.NsdDiscoverer
import com.samyak.television.data.ProviderRepository
import com.samyak.television.model.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPairingScreen(
    onBack: () -> Unit,
    onPairSuccess: () -> Unit
) {
    val context = LocalContext.current
    val providerRepository = remember { ProviderRepository(context) }
    
    // Discovery lists
    val discoveredDevices = remember { mutableStateListOf<NsdServiceInfo>() }
    var selectedDevice by remember { mutableStateOf<NsdServiceInfo?>(null) }
    
    // User Input
    var pairingCode by remember { mutableStateOf("") }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("8082") }
    var isManualMode by remember { mutableStateOf(false) }
    
    // Status states
    var statusText by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var importedCount by remember { mutableStateOf(0) }

    // Start/Stop NSD Discovery
    val nsdDiscoverer = remember {
        NsdDiscoverer(
            context = context,
            onDeviceDiscovered = { service ->
                if (!discoveredDevices.any { it.serviceName == service.serviceName }) {
                    discoveredDevices.add(service)
                }
            },
            onDeviceLost = { service ->
                discoveredDevices.removeAll { it.serviceName == service.serviceName }
                if (selectedDevice?.serviceName == service.serviceName) {
                    selectedDevice = null
                }
            }
        )
    }

    DisposableEffect(Unit) {
        nsdDiscoverer.startDiscovery()
        onDispose {
            nsdDiscoverer.stopDiscovery()
        }
    }

    fun executePairing(ip: String, port: Int, code: String) {
        isConnecting = true
        statusText = "Connecting to mobile device..."
        
        val client = OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val cleanCode = code.replace(" ", "").trim()
        val url = "http://$ip:$port/pair?code=$cleanCode&device=${android.os.Build.MODEL}"
        val request = Request.Builder().url(url).build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "[]"
                        val type = object : com.google.gson.reflect.TypeToken<List<Provider>>() {}.type
                        val providers: List<Provider> = Gson().fromJson(body, type)
                        
                        // Save providers to TV Repository
                        providers.forEach { provider ->
                            providerRepository.addProvider(provider)
                        }

                        withContext(Dispatchers.Main) {
                            isConnecting = false
                            isSuccess = true
                            importedCount = providers.size
                            statusText = "Paired! Successfully imported $importedCount providers."
                            
                            // Auto dismiss after 2.5 seconds
                            kotlinx.coroutines.delay(2500)
                            onPairSuccess()
                            onBack()
                        }
                    } else {
                        val errorDetail = if (response.code == 401) "Invalid pairing code" else "Server error: ${response.code}"
                        withContext(Dispatchers.Main) {
                            isConnecting = false
                            statusText = "Pairing failed: $errorDetail"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConnecting = false
                    statusText = "Failed to connect: ${e.localizedMessage ?: "Network error"}"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B13))
            .padding(32.dp)
    ) {
        if (isSuccess) {
            // Success Overlay
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF26A69A),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sync Complete!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Imported $importedCount playlist sources from your phone.",
                    fontSize = 16.sp,
                    color = Color.LightGray
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color(0xFF26A69A)
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Import Playlists from Phone",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Ensure both devices are on the same local network.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Card: Discovered Devices
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .background(Color(0xFF131926), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(20.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = "Discovered Mobile Devices",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Button(
                                    onClick = {
                                        discoveredDevices.clear()
                                        nsdDiscoverer.stopDiscovery()
                                        nsdDiscoverer.startDiscovery()
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.05f),
                                        focusedContainerColor = Color(0xFF26A69A)
                                    ),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Scan",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scan", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }

                            // Discovered devices list or scan loading indicator
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (discoveredDevices.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF26A69A),
                                            modifier = Modifier.size(36.dp),
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Searching for phone provider...",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.LightGray
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Open the TV Pairing screen on your phone app",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(discoveredDevices) { service ->
                                            val isSelected = selectedDevice?.serviceName == service.serviceName
                                            Surface(
                                                onClick = {
                                                    selectedDevice = service
                                                    isManualMode = false
                                                },
                                                colors = ClickableSurfaceDefaults.colors(
                                                    containerColor = if (isSelected) Color(0xFF26A69A).copy(alpha = 0.15f) else Color(0xFF1A2333),
                                                    focusedContainerColor = Color(0xFF26A69A),
                                                    pressedContainerColor = Color(0xFF1E887E)
                                                ),
                                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) Color(0xFF26A69A) else Color.White.copy(alpha = 0.05f),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(
                                                        text = service.serviceName.replace("IPTVMinePro-", ""),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = "${service.host?.hostAddress ?: "Resolving IP"}:${service.port}",
                                                        fontSize = 11.sp,
                                                        color = Color.LightGray.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Manual entry fallback button
                            Surface(
                                onClick = { 
                                    isManualMode = !isManualMode 
                                    if (isManualMode) selectedDevice = null
                                },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isManualMode) Color(0xFF26A69A).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                                    focusedContainerColor = Color(0xFF26A69A)
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = if (isManualMode) "Switch to Local Discovery" else "Manual IP Entry Fallback",
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Right Card: Connection Setup
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF131926), RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(20.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "Connection Setup",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Middle area: inputs centered vertically
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                            ) {
                                if (isManualMode) {
                                    // Manual IP fields
                                    OutlinedTextField(
                                        value = manualIp,
                                        onValueChange = { manualIp = it },
                                        label = { Text("Phone IP Address", color = Color.Gray) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF26A69A),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                            focusedContainerColor = Color(0xFF222E47),
                                            unfocusedContainerColor = Color(0xFF1A2333)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = manualPort,
                                        onValueChange = { manualPort = it },
                                        label = { Text("Phone Server Port", color = Color.Gray) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFF26A69A),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                            focusedContainerColor = Color(0xFF222E47),
                                            unfocusedContainerColor = Color(0xFF1A2333)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    // Discovery info card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1A2333), RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                            .padding(16.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Selected Target Device:",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (selectedDevice != null) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = Color(0xFF26A69A),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Text(
                                                    text = selectedDevice?.serviceName?.replace("IPTVMinePro-", "") ?: "None Selected",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = if (selectedDevice != null) Color.White else Color(0xFFEF5350),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (selectedDevice == null) {
                                                Text(
                                                    text = "Please select a discovered phone from the list on the left to start.",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Pairing Code input
                                OutlinedTextField(
                                    value = pairingCode,
                                    onValueChange = { if (it.length <= 7) pairingCode = it },
                                    label = { Text("Enter 7-Digit Code", color = Color.Gray) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF26A69A),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                        focusedContainerColor = Color(0xFF222E47),
                                        unfocusedContainerColor = Color(0xFF1A2333)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action button at the bottom
                            val isInputValid = pairingCode.length == 7 && 
                                    ((!isManualMode && selectedDevice != null) || (isManualMode && manualIp.isNotBlank() && manualPort.isNotBlank()))
                            
                            Button(
                                onClick = {
                                    if (isInputValid) {
                                        if (isManualMode) {
                                            executePairing(manualIp.trim(), manualPort.trim().toIntOrNull() ?: 8082, pairingCode)
                                        } else {
                                            selectedDevice?.let { device ->
                                                val ip = device.host?.hostAddress ?: ""
                                                val port = device.port
                                                executePairing(ip, port, pairingCode)
                                            }
                                        }
                                    }
                                },
                                enabled = isInputValid && !isConnecting,
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFF26A69A),
                                    focusedContainerColor = Color(0xFF1E887E),
                                    disabledContainerColor = Color.White.copy(alpha = 0.05f),
                                    contentColor = Color.White,
                                    disabledContentColor = Color.Gray
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                if (isConnecting) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Pairing...")
                                    }
                                } else {
                                    Text("Connect & Import", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (statusText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E293B).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (statusText.contains("failed") || statusText.contains("Failed")) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = "Status",
                                        tint = if (statusText.contains("failed") || statusText.contains("Failed")) Color(0xFFEF5350) else Color(0xFF26A69A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = statusText,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
