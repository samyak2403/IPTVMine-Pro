package com.samyak.iptvminepro.ui.screens.provider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.provider.ProviderRepository
import com.samyak.iptvminepro.provider.ChannelsProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.text.style.TextOverflow
import com.samyak.iptvminepro.ui.theme.ColorPrimaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderListScreen(
    viewModel: ChannelsProvider = viewModel(),
    onNavigateBack: () -> Unit,
    onAddProvider: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { ProviderRepository(context) }
    val providers by viewModel.providers.observeAsState(emptyList())
    var editingProvider by remember { mutableStateOf<com.samyak.iptvminepro.model.Provider?>(null) }
    val providerChannelCounts by viewModel.providerChannelCounts.observeAsState(emptyMap())
    val isLoading by viewModel.isLoading.observeAsState(false)
    if (editingProvider != null) {
        AddProviderScreen(
            onProviderAdded = {
                editingProvider = null
                viewModel.fetchM3UFile()
            },
            onNavigateBack = { editingProvider = null },
            editUrl = editingProvider!!.url
        )
    } else {
        Scaffold(
            containerColor = Color(0xFFF5F7FA), // Premium light background
            floatingActionButton = {
                FloatingActionButton(onClick = onAddProvider, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(id = R.string.desc_add_provider), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        ) { paddingValues ->
            if (providers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(stringResource(id = R.string.msg_no_providers), fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Real-time Provider & Channels Summary Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = ColorPrimaryDark),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.title_provider_summary),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.label_active_providers),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.active_providers_count, providers.count { it.isActive }, providers.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.label_total_active_channels),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.total_channels_count, providerChannelCounts.values.sum()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    items(providers) { provider ->
                        val channelCount = if (provider.isActive) {
                            providerChannelCounts[provider.url] ?: provider.channelCount
                        } else {
                            provider.channelCount
                        }
                        val statusText = if (provider.safeType == com.samyak.iptvminepro.model.ProviderType.VEGA) {
                            "VOD Scraper"
                        } else if (provider.isActive && channelCount == 0 && isLoading) {
                            stringResource(id = R.string.loading)
                        } else {
                            stringResource(id = R.string.total_channels_count, channelCount)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = provider.isActive,
                                    onCheckedChange = { isChecked ->
                                        repository.updateProvider(provider.copy(isActive = isChecked))
                                        viewModel.refreshProviders()
                                        viewModel.fetchM3UFile()
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF26A69A), // Teal
                                        checkmarkColor = Color.White
                                    )
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp)
                                  ) {
                                      Row(
                                          verticalAlignment = Alignment.CenterVertically,
                                          modifier = Modifier.fillMaxWidth()
                                      ) {
                                          Text(
                                              text = provider.title,
                                              style = MaterialTheme.typography.titleMedium.copy(
                                                  fontWeight = FontWeight.Bold,
                                                  fontSize = 16.sp
                                              ),
                                              color = Color(0xFF1F2937), // Slate Dark
                                              modifier = Modifier.weight(1f, fill = false),
                                              maxLines = 1,
                                              overflow = TextOverflow.Ellipsis
                                          )
                                          Spacer(modifier = Modifier.width(8.dp))
                                          // Provider Type Badge
                                          Surface(
                                              color = if (provider.safeType == com.samyak.iptvminepro.model.ProviderType.IPTV) Color(0xFFE0F7FA) else Color(0xFFEDE7F6),
                                              shape = RoundedCornerShape(8.dp)
                                          ) {
                                              Text(
                                                  text = provider.safeType.name,
                                                  color = if (provider.safeType == com.samyak.iptvminepro.model.ProviderType.IPTV) Color(0xFF006064) else Color(0xFF512DA8),
                                                  style = MaterialTheme.typography.labelSmall,
                                                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                  fontWeight = FontWeight.Bold
                                              )
                                          }
                                          Spacer(modifier = Modifier.width(4.dp))
                                          Surface(
                                              color = Color(0xFFE0F2F1), // Light teal background
                                              shape = RoundedCornerShape(8.dp)
                                          ) {
                                              Text(
                                                  text = statusText,
                                                  color = Color(0xFF00796B), // Dark teal text
                                                  style = MaterialTheme.typography.labelSmall,
                                                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                  fontWeight = FontWeight.Bold
                                              )
                                          }
                                      }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = provider.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF6B7280), // Slate Muted
                                        lineHeight = 15.sp
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = { editingProvider = provider },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = stringResource(id = R.string.desc_edit),
                                            tint = Color(0xFF26A69A), // Teal
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            repository.removeProvider(provider.url)
                                            viewModel.refreshProviders()
                                            viewModel.fetchM3UFile()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(id = R.string.desc_delete),
                                            tint = Color(0xFFEF4444), // Crimson
                                            modifier = Modifier.size(20.dp)
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
}
