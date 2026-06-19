package com.samyak.television.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.samyak.television.data.ExtensionRepository
import com.samyak.television.data.ProviderRepository
import com.samyak.television.data.VegaProviderRunner
import com.samyak.television.model.Provider
import com.samyak.television.model.ProviderType
import com.samyak.television.model.VegaProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ExtensionsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val providerRepository = remember { ProviderRepository(context) }
    val extensionRepository = remember { ExtensionRepository.getInstance(context) }
    val runner = remember { VegaProviderRunner(context) }

    var vegaProviders by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var selectedSource by remember { mutableStateOf<Provider?>(null) }
    var allExtensions by remember { mutableStateOf<List<VegaProvider>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val installedExtensionsState by extensionRepository.installedExtensionsFlow.collectAsState()

    // Load sources
    LaunchedEffect(Unit) {
        vegaProviders = providerRepository.getProviders().filter { it.safeType == ProviderType.VEGA }
        selectedSource = vegaProviders.firstOrNull()
    }

    // Load extensions when selected source changes
    LaunchedEffect(selectedSource) {
        val source = selectedSource
        if (source != null) {
            isLoading = true
            scope.launch {
                try {
                    val manifest = runner.fetchManifest(source.url)
                    allExtensions = manifest.distinctBy { it.value }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load extensions: ${e.message}", Toast.LENGTH_LONG).show()
                    allExtensions = emptyList()
                } finally {
                    isLoading = false
                }
            }
        } else {
            allExtensions = emptyList()
            isLoading = false
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: Header and Sources list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = 24.dp)
        ) {
            Text(
                text = "Extensions",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "Choose Provider Source:",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (vegaProviders.isEmpty()) {
                Text(
                    text = "No Vega Providers added yet. Go to Manage Playlists to add a Vega source.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(vegaProviders) { provider ->
                        val isSelected = selectedSource?.url == provider.url
                        Surface(
                            onClick = { selectedSource = provider },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) Color(0xFF26A69A).copy(alpha = 0.2f) else Color(0xFF1E1E1E),
                                focusedContainerColor = Color(0xFF26A69A),
                                pressedContainerColor = Color(0xFF208b80)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = provider.title,
                                fontSize = 14.sp,
                                color = Color.White,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.1f))
        )

        // Right Column: List of extensions available from source
        Column(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxHeight()
                .padding(start = 24.dp)
        ) {
            Text(
                text = selectedSource?.let { "Extensions from ${it.title}" } ?: "Scraper Extensions",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp, end = 100.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF26A69A))
                }
            } else if (allExtensions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedSource == null) "Select a provider source to load extensions" else "No extensions found in this source",
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(allExtensions, key = { it.value }) { ext ->
                        val isInstalled = installedExtensionsState.contains(ext.value)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ext.display_name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "v${ext.version} • ${ext.type}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Button(
                                    onClick = {
                                        extensionRepository.setExtensionInstalled(ext.value, !isInstalled)
                                        val msg = if (!isInstalled) {
                                            "${ext.display_name} Installed"
                                        } else {
                                            "${ext.display_name} Uninstalled"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.colors(
                                        containerColor = if (isInstalled) Color.Red.copy(alpha = 0.1f) else Color(0xFF26A69A),
                                        focusedContainerColor = if (isInstalled) Color.Red else Color(0xFF208b80),
                                        contentColor = if (isInstalled) Color.Red else Color.White,
                                        focusedContentColor = Color.White
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                                ) {
                                    Text(text = if (isInstalled) "Uninstall" else "Install")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
