package com.samyak.television

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.samyak.television.data.ProviderRepository
import com.samyak.television.data.TelevisionViewModel
import com.samyak.television.model.Channel
import com.samyak.television.model.Provider
import com.samyak.television.model.ProviderType
import com.samyak.television.ui.theme.IPTVMineProTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVMineProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    TelevisionApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TelevisionApp(viewModel: TelevisionViewModel = viewModel()) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var activeScreenIndex by remember { mutableStateOf(0) }
    // activeScreenIndex == 0: Channels list grouped by category
    // activeScreenIndex == 1: Manage Playlists screen

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // 1. Sidebar Navigation (Left Panel)
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color(0xFF1E1E1E))
                .padding(vertical = 24.dp, horizontal = 16.dp)
        ) {
            Text(
                text = "IPTV Mine Pro",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF26A69A),
                modifier = Modifier.padding(bottom = 24.dp, start = 8.dp)
            )

            // Dynamic Categories list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(categories.size) { index ->
                    val categoryName = categories[index]
                    val isSelected = activeScreenIndex == 0 && selectedCategory == categoryName
                    
                    Surface(
                        onClick = {
                            activeScreenIndex = 0
                            viewModel.selectCategory(categoryName)
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) Color(0xFF26A69A).copy(alpha = 0.2f) else Color.Transparent,
                            focusedContainerColor = Color(0xFF26A69A),
                            pressedContainerColor = Color(0xFF208b80)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = categoryName,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected || activeScreenIndex == 0) Color.White else Color(0xFFE0E0E0),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manage Playlists Option
            val isManageSelected = activeScreenIndex == 1
            Surface(
                onClick = { activeScreenIndex = 1 },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isManageSelected) Color(0xFF26A69A).copy(alpha = 0.2f) else Color.Transparent,
                    focusedContainerColor = Color(0xFF26A69A),
                    pressedContainerColor = Color(0xFF208b80)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Manage Playlists",
                    fontSize = 16.sp,
                    fontWeight = if (isManageSelected) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.1f))
        )

        // 2. Right Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp)
        ) {
            if (activeScreenIndex == 1) {
                val providerRepository = remember { ProviderRepository(context) }
                var providerList by remember { mutableStateOf(providerRepository.getProviders()) }
                
                ManageProvidersScreen(
                    providerList = providerList,
                    onAddProvider = { title, url ->
                        val newProvider = Provider(title = title, url = url, type = ProviderType.IPTV)
                        providerRepository.addProvider(newProvider)
                        providerList = providerRepository.getProviders()
                        viewModel.refreshChannels()
                    },
                    onDeleteProvider = { url ->
                        providerRepository.removeProvider(url)
                        providerList = providerRepository.getProviders()
                        viewModel.refreshChannels()
                    }
                )
            } else {
                val filteredChannels = remember(channels, selectedCategory) {
                    if (selectedCategory == "All") {
                        channels
                    } else {
                        channels.filter { it.category == selectedCategory }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = selectedCategory,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF26A69A))
                        }
                    } else if (errorMessage != null && filteredChannels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage ?: "Unknown Error",
                                color = Color.Red,
                                fontSize = 16.sp
                            )
                        }
                    } else if (filteredChannels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No TV channels found in this category",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredChannels, key = { it.name + "_" + it.streamUrl }) { channel ->
                                TvChannelCard(channel = channel) {
                                    com.samyak.player.PlayerActivity.start(context, channel.name, channel.streamUrl)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelCard(channel: Channel, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E1E1E),
            focusedContainerColor = Color(0xFF26A69A),
            pressedContainerColor = Color(0xFF208b80)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = channel.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ManageProvidersScreen(
    providerList: List<Provider>,
    onAddProvider: (String, String) -> Unit,
    onDeleteProvider: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: List of existing providers
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .padding(end = 24.dp)
        ) {
            Text(
                text = "IPTV Playlists",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(providerList) { provider ->
                    Surface(
                        onClick = { },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF1E1E1E),
                            focusedContainerColor = Color(0xFF2E2E2E)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = provider.url,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Button(
                                onClick = { onDeleteProvider(provider.url) },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color.Red.copy(alpha = 0.1f),
                                    focusedContainerColor = Color.Red,
                                    contentColor = Color.Red,
                                    focusedContentColor = Color.White
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text(text = "Delete")
                            }
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

        // Right Column: Add playlist form
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 24.dp)
        ) {
            Text(
                text = "Add Playlist",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Playlist Name", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF26A69A),
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("M3U URL", color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF26A69A),
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color(0xFF1E1E1E)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            Button(
                onClick = {
                    if (title.isNotBlank() && url.isNotBlank()) {
                        onAddProvider(title, url)
                        title = ""
                        url = ""
                    }
                },
                enabled = title.isNotBlank() && url.isNotBlank(),
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF26A69A),
                    focusedContainerColor = Color(0xFF208b80),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                    contentColor = Color.White,
                    disabledContentColor = Color.Gray
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Add Playlist")
            }
        }
    }
}