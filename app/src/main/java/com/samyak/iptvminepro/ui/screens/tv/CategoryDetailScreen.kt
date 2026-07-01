package com.samyak.iptvminepro.ui.screens.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import com.samyak.iptvminepro.R
import com.samyak.iptvminepro.provider.ChannelsProvider
import com.samyak.iptvminepro.ui.components.ChannelCard

@Composable
fun CategoryDetailScreen(
    categoryName: String,
    viewModel: ChannelsProvider = viewModel()
) {
    val channels by viewModel.channels.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val context = androidx.compose.ui.platform.LocalContext.current

    // Hoist resource string so it isn't queried via LocalContext inside the click lambda
    val msgMatchNotLive = stringResource(id = R.string.msg_match_not_live)

    val filteredChannels = remember(channels, categoryName) {
        channels.filter { it.category == categoryName }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (filteredChannels.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.msg_no_channels_in_category),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredChannels) { channel ->
                    ChannelCard(
                        channel = channel,
                        onClick = {
                            if (channel.streamUrl.isEmpty()) {
                                android.widget.Toast.makeText(context, msgMatchNotLive, android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                com.samyak.player.PlayerActivity.start(context, channel.name, channel.streamUrl)
                            }
                        }
                    )
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
