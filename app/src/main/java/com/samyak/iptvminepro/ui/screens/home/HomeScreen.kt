package com.samyak.iptvminepro.ui.screens.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.model.Channel
import com.samyak.iptvminepro.model.VegaPost
import com.samyak.iptvminepro.model.VegaCatalog
import com.samyak.iptvminepro.model.VegaProvider
import com.samyak.iptvminepro.model.Provider
import com.samyak.iptvminepro.model.ProviderType
import com.samyak.iptvminepro.provider.ChannelsProvider
import com.samyak.iptvminepro.provider.ExtensionRepository
import com.samyak.iptvminepro.ui.components.ChannelCard
import com.samyak.iptvminepro.ui.components.MovieCard
import com.samyak.iptvminepro.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChannelsProvider = viewModel(),
    homeViewModel: HomeViewModel,            // Must be Activity-scoped (passed from MainApp)
    navController: NavController,
    onChannelClick: (Channel) -> Unit = {}
) {
    val context = LocalContext.current
    val channels by viewModel.channels.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val errorMessage by viewModel.error.observeAsState(null)

    // User-added direct video URLs (grouped into a single "My Videos" home card).
    val providers by viewModel.providers.observeAsState(emptyList())
    val videoProviders = remember(providers) {
        providers.filter { it.safeType == ProviderType.VIDEO && it.isActive }
    }

    // VOD movie state – backed by Activity-scoped ViewModel so it survives back-navigation.
    // Returning from MovieDetailScreen reuses cached data; no API call, no spinner.
    val isMoviesLoading by homeViewModel.isMoviesLoading.collectAsState()
    val moviesByCategory by homeViewModel.moviesByCategory.collectAsState()
    val selectedProvider by homeViewModel.selectedProvider.collectAsState()
    val selectedScraper by homeViewModel.selectedScraper.collectAsState()

    val extensionRepo = remember { ExtensionRepository.getInstance(context) }
    val installedExtensionsState by extensionRepo.installedExtensionsFlow.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
    }

    // Delegate loading to ViewModel – internal guards skip the call on back-navigation
    LaunchedEffect(installedExtensionsState) {
        homeViewModel.loadMoviesIfNeeded(installedExtensionsState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if ((isLoading && channels.isEmpty()) || (isMoviesLoading && moviesByCategory.isEmpty())) {
                com.samyak.iptvminepro.ui.components.HomeShimmerPlaceholder(
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            } else {
                val chunkedChannels = remember(channels) { channels.chunked(2) }

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (videoProviders.isNotEmpty()) {
                        item {
                            MyVideosCard(
                                count = videoProviders.size,
                                onClick = { navController.navigate("video_list") }
                            )
                        }
                    }

                    if (moviesByCategory.isNotEmpty()) {
                        moviesByCategory.forEach { (catalog, movies) ->
                            item {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val provider = selectedProvider
                                                val scraper = selectedScraper
                                                if (provider != null && scraper != null) {
                                                    val encodedTitle = Uri.encode(catalog.title)
                                                    val encodedFilter = Uri.encode(catalog.filter)
                                                    val encodedProviderUrl = Uri.encode(provider.url)
                                                    val scraperValue = scraper.value
                                                    navController.navigate("category_movies?categoryName=$encodedTitle&categoryFilter=$encodedFilter&providerUrl=$encodedProviderUrl&scraperValue=$scraperValue")
                                                }
                                            }
                                            .padding(bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = catalog.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "View All",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(movies) { movie ->
                                            MovieCard(
                                                movie = movie,
                                                onClick = {
                                                    val provider = selectedProvider
                                                    val scraper = selectedScraper
                                                    if (provider != null && scraper != null) {
                                                        val encodedLink = Uri.encode(movie.link)
                                                        val encodedProviderUrl = Uri.encode(provider.url)
                                                        val scraperValue = scraper.value
                                                        navController.navigate("movie_detail?link=$encodedLink&providerUrl=$encodedProviderUrl&scraperValue=$scraperValue")
                                                    }
                                                },
                                                modifier = Modifier.width(130.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (channels.isNotEmpty()) {
                        item {
                            Text(
                                text = "Live TV Channels",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                            )
                        }
                        items(chunkedChannels) { rowChannels ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowChannels.forEach { channel ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        ChannelCard(channel = channel, onClick = {
                                            onChannelClick(channel)
                                        })
                                    }
                                }
                                if (rowChannels.size < 2) {
                                    repeat(2 - rowChannels.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else if (!isLoading && !isMoviesLoading && moviesByCategory.isEmpty() && videoProviders.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.undraw_files_missing_ntwe),
                                    contentDescription = stringResource(id = R.string.desc_no_channels),
                                    modifier = Modifier.size(150.dp).padding(bottom = 16.dp)
                                )
                                Text(
                                    text = "No content available",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Overlay loading indicator for background fetches
            if ((isLoading && channels.isNotEmpty()) || (isMoviesLoading && moviesByCategory.isNotEmpty())) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
private fun MyVideosCard(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF00695C)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircleFilled,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My Videos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (count == 1) "1 video" else "$count videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
