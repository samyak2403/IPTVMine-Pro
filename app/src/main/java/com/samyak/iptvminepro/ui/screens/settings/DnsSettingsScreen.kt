package com.samyak.iptvminepro.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samyak.iptvminepro.network.DnsMode
import com.samyak.iptvminepro.network.DnsPreferenceManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var currentMode by remember { mutableStateOf(DnsPreferenceManager.getMode(context)) }
    var customUrl by remember { mutableStateOf(DnsPreferenceManager.getCustomUrl(context)) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun runTest(mode: DnsMode, url: String? = null) {
        scope.launch {
            isTesting = true
            testResult = null
            testSuccess = null
            val (success, latency) = DnsPreferenceManager.testDns(mode, url)
            isTesting = false
            testSuccess = success
            testResult = when {
                latency == -1L -> "Please enter a valid DoH URL"
                success -> "Resolving in ${latency}ms"
                else -> "Failed to reach resolver"
            }
        }
    }

    LaunchedEffect(Unit) {
        runTest(currentMode, if (currentMode == DnsMode.CUSTOM) customUrl else null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
            // Informational Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Encrypted DNS Protection",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "DNS over HTTPS (DoH) encrypts all domain name lookups with TLS. This stops ISPs and local networks from tracking your activity, prevents DNS poisoning, and unblocks restricted media providers.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Live Status & Quick Test Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Active Resolver",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentMode.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentMode == DnsMode.OFF) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        if (testResult != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (testSuccess == true) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (testSuccess == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = testResult ?: "",
                                    fontSize = 12.sp,
                                    color = if (testSuccess == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { runTest(currentMode, customUrl) },
                        enabled = !isTesting,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Test", fontSize = 13.sp)
                        }
                    }
                }
            }

            Text(
                text = "SELECT DNS RESOLVER",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            // Resolver Options List
            DnsMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            currentMode = mode
                            DnsPreferenceManager.setMode(context, mode)
                            Toast
                                .makeText(context, "DNS set to ${mode.title}", Toast.LENGTH_SHORT)
                                .show()
                            runTest(mode, if (mode == DnsMode.CUSTOM) customUrl else null)
                        }
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(14.dp)
                                )
                            } else Modifier
                        ),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    currentMode = mode
                                    DnsPreferenceManager.setMode(context, mode)
                                    Toast.makeText(context, "DNS set to ${mode.title}", Toast.LENGTH_SHORT).show()
                                    runTest(mode, if (mode == DnsMode.CUSTOM) customUrl else null)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = mode.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (mode.badge.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            contentColor = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = mode.badge,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mode.subtitle,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        // Expandable Custom URL Input
                        if (mode == DnsMode.CUSTOM) {
                            AnimatedVisibility(visible = isSelected) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp, start = 12.dp, end = 12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customUrl,
                                        onValueChange = { customUrl = it },
                                        label = { Text("DoH Endpoint URL") },
                                        placeholder = { Text("https://example.com/dns-query") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Uri,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                keyboardController?.hide()
                                                DnsPreferenceManager.setCustomUrl(context, customUrl)
                                                runTest(DnsMode.CUSTOM, customUrl)
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = {
                                                keyboardController?.hide()
                                                DnsPreferenceManager.setCustomUrl(context, customUrl)
                                                Toast.makeText(context, "Custom DoH URL saved", Toast.LENGTH_SHORT).show()
                                                runTest(DnsMode.CUSTOM, customUrl)
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Save & Apply")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
}
