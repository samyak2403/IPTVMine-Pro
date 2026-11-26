package com.samyak.television.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.samyak.television.ui.components.OttSpecs
import com.samyak.television.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    primaryFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val providerRepository = remember { ProviderRepository(context) }
    val extensionRepository = remember { ExtensionRepository.getInstance(context) }
    val runner = remember { VegaProviderRunner(context) }
    DisposableEffect(Unit) {
        onDispose {
            runner.destroy()
        }
    }

    var vegaProviders by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var selectedSource by remember { mutableStateOf<Provider?>(null) }
    var allExtensions by remember { mutableStateOf<List<VegaProvider>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val installedExtensionsState by extensionRepository.installedExtensionsFlow.collectAsState()

    LaunchedEffect(Unit) {
        val providers = providerRepository.getProviders()
            .filter { it.isActive && it.safeType == ProviderType.VEGA }
        vegaProviders = providers
        val savedUrl = providerRepository.getSelectedVegaProviderUrl()
        selectedSource = providers.firstOrNull { it.url == savedUrl } ?: providers.firstOrNull()
    }

    LaunchedEffect(selectedSource) {
        val source = selectedSource
        if (source != null) {
            isLoading = true
            try {
                val manifest = runner.fetchManifest(source.url)
                allExtensions = manifest.distinctBy { it.value }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load extensions: ${e.message}", Toast.LENGTH_LONG).show()
                allExtensions = emptyList()
            } finally {
                isLoading = false
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
                fontWeight = FontWeight.ExtraBold,
                color = OttWhite,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            Text(
                text = "Choose Provider Source",
                fontSize = 13.sp,
                color = OttTextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (vegaProviders.isEmpty()) {
                Text(
                    text = "No Vega Providers added yet. Go to Playlists to add a Vega source.",
                    fontSize = 14.sp,
                    color = OttTextSecondary
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(vegaProviders) { index, provider ->
                        val isSelected = selectedSource?.url == provider.url
                        var isFocused by remember { mutableStateOf(false) }

                        Surface(
                            onClick = {
                                selectedSource = provider
                                providerRepository.setSelectedVegaProviderUrl(provider.url)
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) OttWhite.copy(alpha = 0.14f) else OttSurface,
                                focusedContainerColor = OttWhite,
                                pressedContainerColor = OttWhite.copy(alpha = 0.85f)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0 && primaryFocusRequester != null) Modifier.focusRequester(primaryFocusRequester) else Modifier)
                                .onFocusChanged { isFocused = it.isFocused }
                        ) {
                            Text(
                                text = provider.title,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFocused) OttBgPrimary else if (isSelected) OttAmberStreaming else OttWhite,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Right Column: List of extensions available from source
        Column(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxHeight()
                .padding(start = 8.dp)
        ) {
            Text(
                text = selectedSource?.let { "Extensions from ${it.title}" } ?: "Scraper Extensions",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OttWhite,
                modifier = Modifier.padding(bottom = 16.dp, end = 88.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OttAmberStreaming)
                }
            } else if (allExtensions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedSource == null) "Select a provider source to load extensions" else "No extensions found in this source",
                        color = OttTextSecondary,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(allExtensions, key = { it.value }) { ext ->
                        val isInstalled = installedExtensionsState.contains(ext.value)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OttSurface, RoundedCornerShape(OttSpecs.CardRadius))
                                .border(1.dp, OttBorderSubtle, RoundedCornerShape(OttSpecs.CardRadius))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = ext.display_name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OttWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isInstalled) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(OttAmberStreaming.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Installed",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OttAmberStreaming
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "v${ext.version} • ${ext.type}",
                                        fontSize = 12.sp,
                                        color = OttTextSecondary
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
                                        containerColor = if (isInstalled) OttLiveRed.copy(alpha = 0.14f) else OttWhite,
                                        focusedContainerColor = if (isInstalled) OttLiveRed else OttAmberStreaming,
                                        contentColor = if (isInstalled) OttLiveRed else OttBgPrimary,
                                        focusedContentColor = if (isInstalled) OttWhite else OttBgPrimary
                                    ),
                                    shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                                ) {
                                    Text(
                                        text = if (isInstalled) "Uninstall" else "Install",
                                        fontWeight = FontWeight.Bold
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
